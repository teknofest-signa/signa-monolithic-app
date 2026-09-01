package teknofest.signa.producer.config;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the OPRF layer.
 *
 * <p>Key material is read from the environment and never from a checked-in
 * default. There is intentionally no fallback value for {@link #activeKey}:
 * a committed default OPRF key would be public, and anyone holding it could
 * compute the pseudonym of any identifier they can guess, which is precisely
 * the attack the layer exists to prevent.
 */
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@ConfigurationProperties(prefix = "application.security.oprf")
public class OprfProperties {

    /**
     * The active server key: a P-256 scalar as 64 hex characters.
     * Supplied through the {@code SIGNA_OPRF_ACTIVE_KEY} environment variable.
     */
    String activeKey;

    /**
     * Superseded keys, kept evaluable so that pseudonyms enrolled under them
     * can still be recomputed while a rotation is in progress. Same encoding as
     * {@link #activeKey}.
     */
    List<String> previousKeys = new ArrayList<>();

    /**
     * Largest batch a single evaluation request may carry. Bounds the CPU a
     * caller can consume per request: every element costs one scalar
     * multiplication, and the request holds a worker thread while it runs.
     */
    int maxBatchSize = 128;

    RateLimit rateLimit = new RateLimit();

    LocalClient localClient = new LocalClient();

    Screening screening = new Screening();

    /**
     * Per-bank ceilings on OPRF use.
     *
     * <p>These are the single most important control in the whole layer. The
     * OPRF is an oracle: a caller who can evaluate without limit can walk a
     * dictionary of candidate identifiers, compute the pseudonym of each, and
     * match them against pseudonyms it has seen — recovering exactly the
     * identifiers the blinding was meant to protect. Azerbaijani FIN codes are
     * seven characters, a space small enough that this is a real attack and not
     * a theoretical one. Blinding hides each individual query from the server;
     * only rate limiting bounds how many queries a bank gets to make.
     */
    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class RateLimit {

        boolean enabled = true;

        /** Elements a single bank may have evaluated per minute. */
        int elementsPerMinute = 600;

        /**
         * Elements a single bank may have evaluated per rolling day. Sized for
         * onboarding an existing customer book over several days rather than in
         * one burst; raise it deliberately, per bank, for a migration.
         */
        int elementsPerDay = 50_000;
    }

    /**
     * The server-side reference client, for development and demonstration only.
     *
     * <p>When enabled, the server will accept a raw identifier and run the
     * bank's half of the protocol on its behalf. That is convenient for a demo
     * with no bank connector yet, and it defeats the entire point of the
     * protocol: the identifier reaches the server. It is off by default,
     * refuses to start under the {@code prod} profile, and logs a warning
     * banner whenever it is on.
     */
    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class LocalClient {

        boolean enabled = false;
    }

    /**
     * The screening endpoint: given a pseudonym, is that person flagged
     * anywhere in the network?
     *
     * <p>It gets a budget of its own rather than sharing the evaluation one,
     * for two reasons. Screening is the routine per-transaction path and
     * evaluation is the occasional enrolment path, so one ceiling sized for
     * both is wrong for each. And a bank that has exhausted its enrolment
     * budget should still be able to screen the customer standing in front of
     * it; refusing that would push the caller towards processing the
     * transaction unchecked, which is the opposite of what this platform is
     * for.
     *
     * <p>The limit still matters. Screening is the second half of the
     * enumeration attack: derive the pseudonym of a candidate identifier, ask
     * whether that person is flagged, and a bank has learned something about
     * someone who is not its customer. Evaluation is capped, so the pairing is
     * capped twice over, and every check is recorded in
     * {@code screening_checks}.
     */
    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Screening {

        /**
         * Ceilings on screening checks, counted one per pseudonym asked about.
         * The defaults match the evaluation ones as a starting point; a
         * production deployment screening every transaction will need the
         * daily figure raised deliberately, per bank.
         */
        RateLimit rateLimit = new RateLimit();
    }
}
