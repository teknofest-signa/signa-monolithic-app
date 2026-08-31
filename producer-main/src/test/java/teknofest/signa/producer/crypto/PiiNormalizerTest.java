package teknofest.signa.producer.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Canonicalisation fixtures, pinned as exact bytes.
 *
 * <p>These constants are duplicated in the frontend's
 * {@code scripts/oprf.parity.mjs}. That duplication is the point: the Java
 * server and the JavaScript bank client must canonicalise byte for byte, or the
 * same customer enrols under two different pseudonyms and the network silently
 * stops matching them. Nothing throws, nothing logs, fraud just gets through.
 *
 * <p>If you change {@link PiiNormalizer}, change both fixtures together.
 */
class PiiNormalizerTest {

    /** UTF-8 of "az-fin:7GK4M2Q". */
    private static final String CANONICAL_FIN = "617a2d66696e3a37474b344d3251";

    @ParameterizedTest(name = "[{index}] {0} canonicalises to az-fin:7GK4M2Q")
    @ValueSource(strings = {
            "7GK4M2Q",
            "7gk4m2q",
            "  7GK4M2Q  ",
            "7GK-4M2Q",
            "7gk 4m2q",
            "7GK 4M2Q",   // non-breaking space, the one a PDF paste carries
            "7GK‍4M2Q",   // zero-width joiner, category Cf
            "‎7GK4M2Q"    // left-to-right mark, category Cf
    })
    void canonicalisesToTheSameBytes(String raw) {
        assertEquals(CANONICAL_FIN,
                Oprf.toHex(PiiNormalizer.canonicalize(PiiNormalizer.IdentifierType.AZ_FIN, raw)));
    }

    @Test
    @DisplayName("full-width digits fold to ASCII through NFKC")
    void foldsCompatibilityForms() {
        assertEquals(
                Oprf.toHex(PiiNormalizer.canonicalize(PiiNormalizer.IdentifierType.AZ_FIN, "1234567")),
                Oprf.toHex(PiiNormalizer.canonicalize(PiiNormalizer.IdentifierType.AZ_FIN, "１２３４５６７")));
    }

    @Test
    @DisplayName("the domain prefix keeps identifier types apart")
    void domainsAreSeparated() {
        String fin = Oprf.toHex(PiiNormalizer.canonicalize(PiiNormalizer.IdentifierType.AZ_FIN, "1234567"));
        String tin = Oprf.toHex(PiiNormalizer.canonicalize(PiiNormalizer.IdentifierType.AZ_TIN, "1234567"));

        assertNotEquals(fin, tin);
    }

    @Test
    @DisplayName("an identifier with nothing significant left is refused")
    void refusesEmptyResult() {
        assertThrows(OprfProtocolException.class,
                () -> PiiNormalizer.canonicalize(PiiNormalizer.IdentifierType.AZ_FIN, "---"));
    }
}
