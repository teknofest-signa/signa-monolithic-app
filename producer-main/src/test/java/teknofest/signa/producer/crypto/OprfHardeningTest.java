package teknofest.signa.producer.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;
import org.bouncycastle.math.ec.ECPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The adversarial half of the crypto tests: everything the layer is supposed to
 * refuse.
 */
class OprfHardeningTest {

    private static final BigInteger SECRET_KEY =
            P256Group.decodeScalar(Oprf.fromHex("ca5d94c8807817669a51b196c34c1b7f8442fde4334a7121ae4736364312fca6"));
    private static final ECPoint PUBLIC_KEY = P256Group.scalarMultiplyGenerator(SECRET_KEY);

    // --- element validation -------------------------------------------------

    @Test
    @DisplayName("the identity element is rejected in both of its encodings")
    void rejectsIdentity() {
        // SEC1 encodes the point at infinity as a single zero octet.
        assertThrows(OprfProtocolException.class, () -> P256Group.decodeElement(new byte[]{0x00}));
        assertThrows(OprfProtocolException.class, () -> P256Group.decodeElement(new byte[33]));
    }

    @Test
    @DisplayName("a point that is not on the curve is rejected before any key arithmetic")
    void rejectsOffCurvePoint() {
        // Multiplying an off-curve point by the secret key is the classic
        // invalid-curve attack: the result lands in a small subgroup and leaks
        // the key one residue at a time.
        byte[] offCurve = Oprf.fromHex("02" + "0".repeat(63) + "1");

        assertThrows(OprfProtocolException.class, () -> P256Group.decodeElement(offCurve));
    }

    @ParameterizedTest(name = "rejects malformed element: {0}")
    @ValueSource(strings = {
            "04aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", // uncompressed prefix
            "0100000000000000000000000000000000000000000000000000000000000000ff", // invalid prefix
            "02aaaa"                                                              // too short
    })
    void rejectsMalformedElements(String hex) {
        assertThrows(OprfProtocolException.class, () -> P256Group.decodeElement(Oprf.fromHex(hex)));
    }

    @Test
    @DisplayName("a scalar at or above the group order is rejected")
    void rejectsUnreducedScalar() {
        byte[] atOrder = P256Group.serializeScalar(P256Group.ORDER.subtract(BigInteger.ONE));
        // order - 1 is the largest valid scalar and must be accepted.
        assertEquals(P256Group.ORDER.subtract(BigInteger.ONE), P256Group.decodeScalar(atOrder));

        byte[] allOnes = new byte[32];
        java.util.Arrays.fill(allOnes, (byte) 0xFF);
        assertThrows(OprfProtocolException.class, () -> P256Group.decodeScalar(allOnes));
    }

    // --- proof soundness ----------------------------------------------------

    @Test
    @DisplayName("a proof produced under a different key does not verify")
    void rejectsProofFromWrongKey() {
        // The attack this blocks: a server hands one bank evaluations under a
        // private key of its own, isolating that bank's pseudonym space while
        // staying able to link it to everyone else's.
        byte[] input = Oprf.ascii("az-fin:7GK4M2Q");
        VoprfClient.BlindResult blindResult = VoprfClient.blind(input);

        BigInteger rogueKey = SECRET_KEY.add(BigInteger.valueOf(7)).mod(P256Group.ORDER);
        ECPoint roguePublicKey = P256Group.scalarMultiplyGenerator(rogueKey);
        VoprfServer.BlindEvaluateResult rogue =
                VoprfServer.blindEvaluate(rogueKey, roguePublicKey, List.of(blindResult.blindedElement()));

        // The rogue proof is internally consistent, but not against the public
        // key the bank pinned.
        assertFalse(Dleq.verifyProof(PUBLIC_KEY, List.of(blindResult.blindedElement()),
                rogue.evaluatedElements(), rogue.proof()));

        assertThrows(OprfProtocolException.class, () -> VoprfClient.finalize(
                input, blindResult.blind(), rogue.evaluatedElements().getFirst(),
                blindResult.blindedElement(), PUBLIC_KEY, rogue.proof()));
    }

    @Test
    @DisplayName("tampering with either half of the proof breaks verification")
    void rejectsTamperedProof() {
        Fixture fixture = fixture(Oprf.ascii("az-fin:7GK4M2Q"));

        DleqProof badResponse = new DleqProof(fixture.proof.challenge(),
                fixture.proof.response().add(BigInteger.ONE).mod(P256Group.ORDER));
        DleqProof badChallenge = new DleqProof(
                fixture.proof.challenge().add(BigInteger.ONE).mod(P256Group.ORDER),
                fixture.proof.response());

        assertFalse(Dleq.verifyProof(PUBLIC_KEY, fixture.blinded, fixture.evaluated, badResponse));
        assertFalse(Dleq.verifyProof(PUBLIC_KEY, fixture.blinded, fixture.evaluated, badChallenge));
    }

    @Test
    @DisplayName("a substituted evaluated element breaks verification")
    void rejectsSubstitutedElement() {
        Fixture fixture = fixture(Oprf.ascii("az-fin:7GK4M2Q"));
        List<ECPoint> substituted = List.of(P256Group.scalarMultiplyGenerator(BigInteger.valueOf(42)));

        assertFalse(Dleq.verifyProof(PUBLIC_KEY, fixture.blinded, substituted, fixture.proof));
    }

    @Test
    @DisplayName("reordering a batch breaks the batched proof")
    void rejectsReorderedBatch() {
        byte[] first = Oprf.ascii("az-fin:7GK4M2Q");
        byte[] second = Oprf.ascii("az-fin:5MG7XZ1");

        VoprfClient.BlindResult a = VoprfClient.blind(first);
        VoprfClient.BlindResult b = VoprfClient.blind(second);
        List<ECPoint> blinded = List.of(a.blindedElement(), b.blindedElement());

        VoprfServer.BlindEvaluateResult result = VoprfServer.blindEvaluate(SECRET_KEY, PUBLIC_KEY, blinded);
        List<ECPoint> swapped = List.of(result.evaluatedElements().get(1), result.evaluatedElements().get(0));

        assertTrue(Dleq.verifyProof(PUBLIC_KEY, blinded, result.evaluatedElements(), result.proof()));
        assertFalse(Dleq.verifyProof(PUBLIC_KEY, blinded, swapped, result.proof()));
    }

    @Test
    @DisplayName("a proof deserialised from the wire round-trips exactly")
    void proofSerializationRoundTrips() {
        Fixture fixture = fixture(Oprf.ascii("az-fin:7GK4M2Q"));

        assertEquals(fixture.proof, DleqProof.fromHex(fixture.proof.toHex()));
        assertThrows(OprfProtocolException.class, () -> DleqProof.fromHex("00"));
    }

    // --- blinding -----------------------------------------------------------

    @Test
    @DisplayName("blinding the same identifier twice produces unlinkable requests")
    void blindingHidesRepeatedQueries() {
        byte[] input = Oprf.ascii("az-fin:7GK4M2Q");

        String first = Oprf.toHex(P256Group.serializeElement(VoprfClient.blind(input).blindedElement()));
        String second = Oprf.toHex(P256Group.serializeElement(VoprfClient.blind(input).blindedElement()));

        // If these matched, the server could tell that two enrolment requests
        // concerned the same person just by comparing the bytes on the wire.
        assertNotEquals(first, second);
    }

    // --- normalisation ------------------------------------------------------

    @Test
    @DisplayName("identifiers differing only in case or spacing canonicalise identically")
    void normalizationIsStable() {
        byte[] canonical = PiiNormalizer.canonicalize(PiiNormalizer.IdentifierType.AZ_FIN, "7GK4M2Q");

        assertArrayEquals(canonical, PiiNormalizer.canonicalize(PiiNormalizer.IdentifierType.AZ_FIN, "7gk4m2q"));
        assertArrayEquals(canonical, PiiNormalizer.canonicalize(PiiNormalizer.IdentifierType.AZ_FIN, "  7GK4M2Q "));
        assertArrayEquals(canonical, PiiNormalizer.canonicalize(PiiNormalizer.IdentifierType.AZ_FIN, "7GK-4M2Q"));
    }

    @Test
    @DisplayName("the same string under two identifier types yields different pseudonyms")
    void identifierTypesAreDomainSeparated() {
        byte[] fin = PiiNormalizer.canonicalize(PiiNormalizer.IdentifierType.AZ_FIN, "1234567");
        byte[] tin = PiiNormalizer.canonicalize(PiiNormalizer.IdentifierType.AZ_TIN, "1234567");

        assertNotEquals(Oprf.toHex(fin), Oprf.toHex(tin));
        assertNotEquals(
                Oprf.toHex(VoprfServer.evaluate(SECRET_KEY, fin)),
                Oprf.toHex(VoprfServer.evaluate(SECRET_KEY, tin)));
    }

    @Test
    @DisplayName("an empty or whitespace-only identifier is refused")
    void rejectsEmptyIdentifier() {
        assertThrows(OprfProtocolException.class,
                () -> PiiNormalizer.canonicalize(PiiNormalizer.IdentifierType.AZ_FIN, "   "));
        assertThrows(OprfProtocolException.class,
                () -> PiiNormalizer.canonicalize(PiiNormalizer.IdentifierType.AZ_FIN, null));
    }

    // --- key handling -------------------------------------------------------

    @Test
    @DisplayName("a key never prints its secret")
    void keyDoesNotLeakSecretInToString() {
        var key = teknofest.signa.producer.crypto.key.OprfKey.fromSecret(SECRET_KEY);

        assertFalse(key.toString().contains(SECRET_KEY.toString(16)));
        assertTrue(key.toString().contains(key.keyId()));
    }

    @Test
    @DisplayName("key ids are derived from the key, so distinct keys cannot collide")
    void keyIdsAreDerived() {
        var first = teknofest.signa.producer.crypto.key.OprfKey.fromSecret(SECRET_KEY);
        var second = teknofest.signa.producer.crypto.key.OprfKey.fromSecret(
                SECRET_KEY.add(BigInteger.ONE).mod(P256Group.ORDER));

        assertNotEquals(first.keyId(), second.keyId());
        assertEquals(first.keyId(), teknofest.signa.producer.crypto.key.OprfKey.fromSecret(SECRET_KEY).keyId());
    }

    @Test
    @DisplayName("a key outside [1, order - 1] is refused")
    void rejectsOutOfRangeKey() {
        assertThrows(IllegalArgumentException.class,
                () -> teknofest.signa.producer.crypto.key.OprfKey.fromSecret(BigInteger.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> teknofest.signa.producer.crypto.key.OprfKey.fromSecret(P256Group.ORDER));
    }

    private record Fixture(List<ECPoint> blinded, List<ECPoint> evaluated, DleqProof proof) {
    }

    private static Fixture fixture(byte[] input) {
        VoprfClient.BlindResult blindResult = VoprfClient.blind(input);
        List<ECPoint> blinded = List.of(blindResult.blindedElement());
        VoprfServer.BlindEvaluateResult result = VoprfServer.blindEvaluate(SECRET_KEY, PUBLIC_KEY, blinded);

        return new Fixture(blinded, result.evaluatedElements(), result.proof());
    }
}
