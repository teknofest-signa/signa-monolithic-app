package teknofest.signa.producer.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import teknofest.signa.producer.config.OprfProperties;

/**
 * The rate limiter is the control that keeps a member bank from using the OPRF
 * as a lookup table for the entire national identifier space, so its edges are
 * worth pinning down.
 */
class OprfRateLimiterTest {

    private static OprfRateLimiter limiter(int perMinute, int perDay) {
        OprfProperties properties = new OprfProperties();
        properties.getRateLimit().setElementsPerMinute(perMinute);
        properties.getRateLimit().setElementsPerDay(perDay);

        return new OprfRateLimiter(properties);
    }

    @Test
    @DisplayName("counts elements, not requests, so batching does not buy extra budget")
    void countsElements() {
        OprfRateLimiter rateLimiter = limiter(10, 1000);
        UUID bank = UUID.randomUUID();

        assertTrue(rateLimiter.tryConsume(bank, 6).allowed());
        assertTrue(rateLimiter.tryConsume(bank, 4).allowed());
        assertFalse(rateLimiter.tryConsume(bank, 1).allowed());
    }

    @Test
    @DisplayName("budgets are per bank, so one bank cannot exhaust another's")
    void isolatesBanks() {
        OprfRateLimiter rateLimiter = limiter(5, 1000);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(rateLimiter.tryConsume(first, 5).allowed());
        assertFalse(rateLimiter.tryConsume(first, 1).allowed());
        assertTrue(rateLimiter.tryConsume(second, 5).allowed());
    }

    @Test
    @DisplayName("a batch larger than the whole budget is refused rather than partly served")
    void refusesOversizedBatch() {
        OprfRateLimiter rateLimiter = limiter(10, 1000);

        assertFalse(rateLimiter.tryConsume(UUID.randomUUID(), 11).allowed());
    }

    @Test
    @DisplayName("hitting the daily cap refunds the minute window, so a refusal costs nothing")
    void refundsMinuteWindowWhenDailyCapRejects() {
        OprfRateLimiter rateLimiter = limiter(10, 6);
        UUID bank = UUID.randomUUID();

        assertTrue(rateLimiter.tryConsume(bank, 6).allowed());

        // Fits the minute window (6 + 4 = 10) but breaks the daily cap, so the
        // minute reservation has to be handed back.
        assertTrue(rateLimiter.tryConsume(bank, 4).reason().contains("daily"));

        // The refund is what this asserts. If the refused call had kept its
        // 4 elements, the minute window would sit at 10 and this call would be
        // turned away by the per-minute limit instead of the daily one.
        assertTrue(rateLimiter.tryConsume(bank, 4).reason().contains("daily"));
    }

    @Test
    @DisplayName("the reason names which limit was hit")
    void reportsReason() {
        OprfRateLimiter rateLimiter = limiter(1, 1000);
        UUID bank = UUID.randomUUID();

        rateLimiter.tryConsume(bank, 1);
        OprfRateLimiter.Decision decision = rateLimiter.tryConsume(bank, 1);

        assertFalse(decision.allowed());
        assertTrue(decision.reason().contains("per-minute"));
    }

    @Test
    @DisplayName("disabling the limiter is possible but explicit")
    void canBeDisabled() {
        OprfProperties properties = new OprfProperties();
        properties.getRateLimit().setEnabled(false);
        properties.getRateLimit().setElementsPerMinute(1);

        OprfRateLimiter rateLimiter = new OprfRateLimiter(properties);

        assertTrue(rateLimiter.tryConsume(UUID.randomUUID(), 100_000).allowed());
    }
}
