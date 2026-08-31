package teknofest.signa.producer.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.bouncycastle.math.ec.ECPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Replays the official test vectors from RFC 9497 Appendix A.3.2 (VOPRF mode,
 * ciphersuite P256-SHA256).
 *
 * <p>These are the tests that matter most in this repository. A self-consistent
 * OPRF that agrees only with itself is worthless here: the whole point is that a
 * bank running an independent implementation, in a different language, arrives
 * at the same pseudonym for the same customer. Matching the published vectors
 * byte for byte is the only evidence of that. If one of these fails after a
 * change to the crypto package, the change broke interoperability, and every
 * pseudonym already enrolled becomes unmatchable.
 */
class VoprfRfc9497VectorTest {

    private static final String SEED = "a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3a3";
    private static final String KEY_INFO = "74657374206b6579";
    private static final String SECRET_KEY = "ca5d94c8807817669a51b196c34c1b7f8442fde4334a7121ae4736364312fca6";
    private static final String PUBLIC_KEY = "03e17e70604bcabe198882c0a1f27a92441e774224ed9c702e51dd17038b102462";

    private static BigInteger secretKey() {
        return P256Group.decodeScalar(Oprf.fromHex(SECRET_KEY));
    }

    private static ECPoint publicKey() {
        return P256Group.decodeElement(Oprf.fromHex(PUBLIC_KEY));
    }

    @Test
    @DisplayName("contextString matches OPRFV1-<mode>-P256-SHA256")
    void contextString() {
        assertEquals("OPRFV1--P256-SHA256", new String(Oprf.CONTEXT_STRING, StandardCharsets.ISO_8859_1));
    }

    @Test
    @DisplayName("DeriveKeyPair reproduces the published key pair")
    void deriveKeyPair() {
        BigInteger derived = VoprfServer.deriveSecretKey(Oprf.fromHex(SEED), Oprf.fromHex(KEY_INFO));

        assertEquals(SECRET_KEY, Oprf.toHex(P256Group.serializeScalar(derived)));
        assertEquals(PUBLIC_KEY, Oprf.toHex(P256Group.serializeElement(P256Group.scalarMultiplyGenerator(derived))));
    }

    @Nested
    @DisplayName("Appendix A.3.2.1 and A.3.2.2, batch size 1")
    class SingleElementVectors {

        @Test
        void testVectorOne() {
            assertVector(
                    "00",
                    "3338fa65ec36e0290022b48eb562889d89dbfa691d1cde91517fa222ed7ad364",
                    "02dd05901038bb31a6fae01828fd8d0e49e35a486b5c5d4b4994013648c01277da",
                    "0209f33cab60cf8fe69239b0afbcfcd261af4c1c5632624f2e9ba29b90ae83e4a2",
                    "e7c2b3c5c954c035949f1f74e6bce2ed539a3be267d1481e9ddb178533df4c266"
                            + "4f69d065c604a4fd953e100b856ad83804eb3845189babfa5a702090d6fc5fa",
                    "f9db001266677f62c095021db018cd8cbb55941d4073698ce45c405d1348b7b1",
                    "0412e8f78b02c415ab3a288e228978376f99927767ff37c5718d420010a645a1");
        }

        @Test
        void testVectorTwo() {
            assertVector(
                    "5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a",
                    "3338fa65ec36e0290022b48eb562889d89dbfa691d1cde91517fa222ed7ad364",
                    "03cd0f033e791c4d79dfa9c6ed750f2ac009ec46cd4195ca6fd3800d1e9b887dbd",
                    "030d2985865c693bf7af47ba4d3a3813176576383d19aff003ef7b0784a0d83cf1",
                    "2787d729c57e3d9512d3aa9e8708ad226bc48e0f1750b0767aaff73482c44b8d"
                            + "2873d74ec88aebd3504961acea16790a05c542d9fbff4fe269a77510db00abab",
                    "f9db001266677f62c095021db018cd8cbb55941d4073698ce45c405d1348b7b1",
                    "771e10dcd6bcd3664e23b8f2a710cfaaa8357747c4a8cbba03133967b5c24f18");
        }

        private void assertVector(String inputHex, String blindHex, String expectedBlindedElement,
                                  String expectedEvaluatedElement, String expectedProof,
                                  String proofRandomScalar, String expectedOutput) {
            byte[] input = Oprf.fromHex(inputHex);
            BigInteger blind = P256Group.decodeScalar(Oprf.fromHex(blindHex));

            VoprfClient.BlindResult blindResult = VoprfClient.blind(input, blind);
            assertEquals(expectedBlindedElement,
                    Oprf.toHex(P256Group.serializeElement(blindResult.blindedElement())),
                    "BlindedElement");

            ECPoint evaluatedElement = P256Group.scalarMultiply(blindResult.blindedElement(), secretKey());
            assertEquals(expectedEvaluatedElement,
                    Oprf.toHex(P256Group.serializeElement(evaluatedElement)),
                    "EvaluatedElement");

            DleqProof proof = Dleq.generateProof(secretKey(), publicKey(),
                    List.of(blindResult.blindedElement()), List.of(evaluatedElement),
                    P256Group.decodeScalar(Oprf.fromHex(proofRandomScalar)));
            assertEquals(expectedProof, proof.toHex(), "Proof");

            byte[] output = VoprfClient.finalize(input, blind, evaluatedElement,
                    blindResult.blindedElement(), publicKey(), proof);
            assertEquals(expectedOutput, Oprf.toHex(output), "Output");
        }
    }

    @Test
    @DisplayName("Appendix A.3.2.3, batch size 2, one proof covering both elements")
    void batchedVector() {
        String[] inputs = {"00", "5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a"};
        String[] blinds = {
                "3338fa65ec36e0290022b48eb562889d89dbfa691d1cde91517fa222ed7ad364",
                "f9db001266677f62c095021db018cd8cbb55941d4073698ce45c405d1348b7b1"};
        String[] expectedBlinded = {
                "02dd05901038bb31a6fae01828fd8d0e49e35a486b5c5d4b4994013648c01277da",
                "03462e9ae64cae5b83ba98a6b360d942266389ac369b923eb3d557213b1922f8ab"};
        String[] expectedEvaluated = {
                "0209f33cab60cf8fe69239b0afbcfcd261af4c1c5632624f2e9ba29b90ae83e4a2",
                "02bb24f4d838414aef052a8f044a6771230ca69c0a5677540fff738dd31bb69771"};
        String expectedProof = "bdcc351707d02a72ce49511c7db990566d29d6153ad6f8982fad2b435d6ce4d6"
                + "0da1e6b3fa740811bde34dd4fe0aa1b5fe6600d0440c9ddee95ea7fad7a60cf2";
        String proofRandomScalar = "350e8040f828bf6ceca27405420cdf3d63cb3aef005f40ba51943c8026877963";
        String[] expectedOutputs = {
                "0412e8f78b02c415ab3a288e228978376f99927767ff37c5718d420010a645a1",
                "771e10dcd6bcd3664e23b8f2a710cfaaa8357747c4a8cbba03133967b5c24f18"};

        byte[][] inputBytes = new byte[inputs.length][];
        BigInteger[] blindScalars = new BigInteger[inputs.length];
        List<ECPoint> blindedElements = new ArrayList<>();
        List<ECPoint> evaluatedElements = new ArrayList<>();

        for (int i = 0; i < inputs.length; i++) {
            inputBytes[i] = Oprf.fromHex(inputs[i]);
            blindScalars[i] = P256Group.decodeScalar(Oprf.fromHex(blinds[i]));

            ECPoint blinded = VoprfClient.blind(inputBytes[i], blindScalars[i]).blindedElement();
            assertEquals(expectedBlinded[i], Oprf.toHex(P256Group.serializeElement(blinded)));
            blindedElements.add(blinded);

            ECPoint evaluated = P256Group.scalarMultiply(blinded, secretKey());
            assertEquals(expectedEvaluated[i], Oprf.toHex(P256Group.serializeElement(evaluated)));
            evaluatedElements.add(evaluated);
        }

        DleqProof proof = Dleq.generateProof(secretKey(), publicKey(), blindedElements, evaluatedElements,
                P256Group.decodeScalar(Oprf.fromHex(proofRandomScalar)));
        assertEquals(expectedProof, proof.toHex(), "batched Proof");
        assertTrue(Dleq.verifyProof(publicKey(), blindedElements, evaluatedElements, proof));

        byte[][] outputs = VoprfClient.finalizeBatch(
                inputBytes, blindScalars, evaluatedElements, blindedElements, publicKey(), proof);

        for (int i = 0; i < inputs.length; i++) {
            assertEquals(expectedOutputs[i], Oprf.toHex(outputs[i]));
        }
    }

    @Test
    @DisplayName("the blinded round trip agrees with a direct evaluation, over fresh randomness")
    void blindedPathMatchesDirectEvaluation() {
        BigInteger secretKey = secretKey();
        ECPoint publicKey = publicKey();

        for (int i = 0; i < 50; i++) {
            byte[] input = PiiNormalizer.canonicalize(PiiNormalizer.IdentifierType.AZ_FIN, "7GK4M" + i + "Q");

            VoprfClient.BlindResult blindResult = VoprfClient.blind(input);
            VoprfServer.BlindEvaluateResult evaluation =
                    VoprfServer.blindEvaluate(secretKey, publicKey, List.of(blindResult.blindedElement()));

            byte[] viaProtocol = VoprfClient.finalize(input, blindResult.blind(),
                    evaluation.evaluatedElements().getFirst(), blindResult.blindedElement(),
                    publicKey, evaluation.proof());

            assertArrayEquals(VoprfServer.evaluate(secretKey, input), viaProtocol);
        }
    }

    @Test
    @DisplayName("the same identifier yields the same pseudonym under different blinds")
    void pseudonymIsStableAcrossBlinds() {
        BigInteger secretKey = secretKey();
        ECPoint publicKey = publicKey();
        byte[] input = PiiNormalizer.canonicalize(PiiNormalizer.IdentifierType.AZ_FIN, "5MG7XZ1");

        String first = deriveThroughProtocol(secretKey, publicKey, input);
        String second = deriveThroughProtocol(secretKey, publicKey, input);

        // This is the property the whole platform rests on: two banks, enrolling
        // the same person independently and with unrelated blinds, land on the
        // same pseudonym without either learning anything from the other.
        assertEquals(first, second);
    }

    private static String deriveThroughProtocol(BigInteger secretKey, ECPoint publicKey, byte[] input) {
        VoprfClient.BlindResult blindResult = VoprfClient.blind(input);
        VoprfServer.BlindEvaluateResult evaluation =
                VoprfServer.blindEvaluate(secretKey, publicKey, List.of(blindResult.blindedElement()));

        return Oprf.toHex(VoprfClient.finalize(input, blindResult.blind(),
                evaluation.evaluatedElements().getFirst(), blindResult.blindedElement(),
                publicKey, evaluation.proof()));
    }
}
