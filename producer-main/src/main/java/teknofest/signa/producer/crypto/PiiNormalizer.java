package teknofest.signa.producer.crypto;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

/**
 * Canonical encoding of an identifier before it enters the OPRF.
 *
 * <p>The OPRF is a function of exact bytes. {@code "7gk4m2q"},
 * {@code "7GK4M2Q"} and {@code "7GK4M2Q "} are three different inputs and
 * produce three unrelated pseudonyms. If two banks disagree about
 * capitalisation or stray whitespace, the same customer enrols under different
 * pseudonyms and the network simply never links them — a failure with no error
 * message and no symptom other than fraud going undetected. Every participant
 * must normalise identically, so the rule lives here rather than in each
 * bank's connector.
 *
 * <p>The domain prefix is equally load-bearing. Without it, a FIN and a tax
 * number that happened to share a string would collide into one pseudonym.
 */
public final class PiiNormalizer {

    private PiiNormalizer() {
    }

    /** Identifier types the platform recognises. Extend deliberately: a new prefix is a new pseudonym space. */
    public enum IdentifierType {
        /** Azerbaijani FIN, the seven-character code on the national ID card. */
        AZ_FIN("az-fin"),
        /** Azerbaijani taxpayer identification number. */
        AZ_TIN("az-tin"),
        /** Passport number, qualified by issuing country elsewhere in the input. */
        PASSPORT("passport");

        private final String domain;

        IdentifierType(String domain) {
            this.domain = domain;
        }

        public String domain() {
            return domain;
        }
    }

    /**
     * Produces the exact bytes to hand to {@link VoprfClient#blind(byte[])}.
     *
     * <p>Applies, in order: Unicode NFKC so visually identical characters have
     * one encoding, whitespace removal, upper-casing in the invariant locale
     * (Turkish and Azerbaijani locales map a dotless i differently, which would
     * make the pseudonym depend on the server's locale setting), and a domain
     * prefix.
     */
    public static byte[] canonicalize(IdentifierType type, String rawIdentifier) {
        if (rawIdentifier == null || rawIdentifier.isBlank()) {
            throw new OprfProtocolException("identifier must not be empty");
        }

        String normalized = Normalizer.normalize(rawIdentifier, Normalizer.Form.NFKC)
                .replaceAll("[\\s\\p{Cf}-]", "")
                .toUpperCase(java.util.Locale.ROOT);

        if (normalized.isEmpty()) {
            throw new OprfProtocolException("identifier contains no significant characters");
        }

        return (type.domain() + ":" + normalized).getBytes(StandardCharsets.UTF_8);
    }
}
