package teknofest.signa.producer.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import teknofest.signa.producer.config.OprfProperties;

/**
 * Per-bank ceilings on the two privacy-layer oracles, counted in units of work
 * rather than requests so that a caller cannot sidestep them by packing a large
 * batch into a single call.
 *
 * <p>Why this exists: the blinding stops the server from seeing what a bank
 * asked about, but it does nothing to stop the bank from asking about
 * everything. A caller that can evaluate without limit can run through a
 * dictionary of candidate identifiers, compute each one's pseudonym, and match
 * them against the pseudonyms it already holds — recovering the very
 * identifiers the protocol was built to hide. Bounding the number of
 * evaluations is the only defence against that, and it is what makes the
 * privacy claim hold against a member bank rather than only against an
 * outsider.
 *
 * <p>Screening is bounded separately and for the same reason. A pseudonym on
 * its own is inert; a pseudonym plus an endpoint that says whether that person
 * is flagged is the second half of the same attack. The two budgets are kept
 * apart so that neither can starve the other: exhausting the enrolment budget
 * must not leave a bank unable to screen the transaction in front of it.
 *
 * <p><b>Deployment note.</b> The counters live in this process. Behind more
 * than one instance each replica enforces its own share, so the effective limit
 * multiplies by the replica count. Move the buckets to Redis before scaling
 * out; see {@code docs/oprf-layer.md}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OprfRateLimiter {

    private final OprfProperties properties;

    private final Map<Bucket, Window> minuteWindows = new ConcurrentHashMap<>();
    private final Map<Bucket, Window> dayWindows = new ConcurrentHashMap<>();

    /**
     * Charges {@code elementCount} against a bank's evaluation budget.
     *
     * @return the decision, so the caller can report which limit was hit
     */
    public Decision tryConsume(UUID bankId, int elementCount) {
        return charge(bankId, Budget.EVALUATION, elementCount, properties.getRateLimit(), "evaluation");
    }

    /**
     * Charges {@code checkCount} against a bank's screening budget, one unit
     * per pseudonym asked about.
     */
    public Decision tryConsumeScreening(UUID bankId, int checkCount) {
        return charge(bankId, Budget.SCREENING, checkCount,
                properties.getScreening().getRateLimit(), "screening");
    }

    private Decision charge(UUID bankId, Budget budget, int amount,
                            OprfProperties.RateLimit limits, String noun) {
        if (!limits.isEnabled()) {
            return Decision.permit();
        }

        Instant now = Instant.now();
        Bucket bucket = new Bucket(bankId, budget);

        Window minuteWindow = minuteWindows.computeIfAbsent(bucket, _ -> new Window());
        Window dayWindow = dayWindows.computeIfAbsent(bucket, _ -> new Window());

        // Reserve on the shorter window first, and give it back if the longer
        // one refuses, so a rejected request costs the caller nothing.
        if (!minuteWindow.tryConsume(amount, limits.getElementsPerMinute(), Duration.ofMinutes(1), now)) {
            log.warn("Per-minute {} limit reached for bank {}", noun, bankId);
            return Decision.deny("per-minute " + noun + " limit reached");
        }
        if (!dayWindow.tryConsume(amount, limits.getElementsPerDay(), Duration.ofDays(1), now)) {
            minuteWindow.refund(amount);
            log.warn("Daily {} limit reached for bank {}", noun, bankId);
            return Decision.deny("daily " + noun + " limit reached");
        }

        return Decision.permit();
    }

    /** Which ceiling a charge is drawn against. */
    private enum Budget {
        EVALUATION,
        SCREENING
    }

    /** One bank's counter for one budget. */
    private record Bucket(UUID bankId, Budget budget) {
    }

    public record Decision(boolean allowed, String reason) {

        static Decision permit() {
            return new Decision(true, null);
        }

        static Decision deny(String reason) {
            return new Decision(false, reason);
        }
    }

    /** A fixed window that resets once its duration has elapsed. */
    private static final class Window {

        private Instant windowStart = Instant.EPOCH;
        private long consumed;

        synchronized boolean tryConsume(int amount, int limit, Duration duration, Instant now) {
            if (Duration.between(windowStart, now).compareTo(duration) >= 0) {
                windowStart = now;
                consumed = 0;
            }
            if (consumed + amount > limit) {
                return false;
            }
            consumed += amount;
            return true;
        }

        synchronized void refund(int amount) {
            consumed = Math.max(0, consumed - amount);
        }
    }
}
