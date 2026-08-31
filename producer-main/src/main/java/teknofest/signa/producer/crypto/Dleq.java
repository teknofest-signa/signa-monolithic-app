package teknofest.signa.producer.crypto;

import java.math.BigInteger;
import java.util.List;
import org.bouncycastle.math.ec.ECPoint;

/**
 * Batched DLEQ proof generation and verification, RFC 9497 Section 2.2.
 *
 * <p>The proof is <em>batched</em>: one proof covers a whole list of
 * (blinded, evaluated) pairs. The verifier folds the list into a single random
 * linear combination using coefficients derived from the transcript, so a
 * server that cheated on even one element of the batch cannot produce a proof
 * that verifies. This is what makes bulk enrolment of a bank's customer base
 * practical without giving up verifiability.
 */
public final class Dleq {

    private Dleq() {
    }

    /**
     * GenerateProof(k, A, B, C, D) with A = generator and B = public key.
     *
     * @param secretKey         the server key k
     * @param publicKey         pkS = k * G
     * @param blindedElements   C: the elements as received from the client
     * @param evaluatedElements D: the elements after evaluation, D[i] = k * C[i]
     */
    public static DleqProof generateProof(BigInteger secretKey,
                                          ECPoint publicKey,
                                          List<ECPoint> blindedElements,
                                          List<ECPoint> evaluatedElements) {
        return generateProof(secretKey, publicKey, blindedElements, evaluatedElements, P256Group.randomScalar());
    }

    /**
     * Deterministic-nonce variant, used only to replay the RFC test vectors.
     *
     * <p>Not for production use: the nonce must be freshly random for every
     * proof. Reusing a nonce across two different challenges lets anyone solve
     * for the secret key from the two responses, exactly as with ECDSA.
     */
    static DleqProof generateProof(BigInteger secretKey,
                                   ECPoint publicKey,
                                   List<ECPoint> blindedElements,
                                   List<ECPoint> evaluatedElements,
                                   BigInteger proofRandomScalar) {
        requireMatchingLists(blindedElements, evaluatedElements);

        Composites composites = computeCompositesFast(secretKey, publicKey, blindedElements, evaluatedElements);

        ECPoint t2 = P256Group.scalarMultiplyGenerator(proofRandomScalar);
        ECPoint t3 = P256Group.scalarMultiply(composites.m(), proofRandomScalar);

        BigInteger challenge = challenge(publicKey, composites.m(), composites.z(), t2, t3);
        BigInteger response = proofRandomScalar
                .subtract(challenge.multiply(secretKey))
                .mod(P256Group.ORDER);

        return new DleqProof(challenge, response);
    }

    /**
     * VerifyProof(A, B, C, D, proof) with A = generator and B = public key.
     * Run by the bank before it accepts an evaluation.
     */
    public static boolean verifyProof(ECPoint publicKey,
                                      List<ECPoint> blindedElements,
                                      List<ECPoint> evaluatedElements,
                                      DleqProof proof) {
        requireMatchingLists(blindedElements, evaluatedElements);

        Composites composites = computeComposites(publicKey, blindedElements, evaluatedElements);

        // t2 = s*G + c*pkS, t3 = s*M + c*Z. These reconstruct the prover's
        // commitments only if the same k relates both pairs.
        ECPoint t2 = P256Group.scalarMultiplyGenerator(proof.response())
                .add(P256Group.scalarMultiply(publicKey, proof.challenge()))
                .normalize();
        ECPoint t3 = P256Group.scalarMultiply(composites.m(), proof.response())
                .add(P256Group.scalarMultiply(composites.z(), proof.challenge()))
                .normalize();

        if (t2.isInfinity() || t3.isInfinity()) {
            return false;
        }

        BigInteger expectedChallenge = challenge(publicKey, composites.m(), composites.z(), t2, t3);

        return Oprf.constantTimeEquals(
                P256Group.serializeScalar(expectedChallenge),
                P256Group.serializeScalar(proof.challenge())
        );
    }

    /**
     * ComputeCompositesFast: the prover's variant, which derives Z as k * M
     * instead of accumulating it, saving one scalar multiplication per element.
     */
    private static Composites computeCompositesFast(BigInteger secretKey,
                                                    ECPoint publicKey,
                                                    List<ECPoint> blindedElements,
                                                    List<ECPoint> evaluatedElements) {
        byte[] seed = compositeSeed(publicKey);

        ECPoint m = P256Group.identity();
        for (int i = 0; i < blindedElements.size(); i++) {
            BigInteger coefficient = compositeCoefficient(seed, i, blindedElements.get(i), evaluatedElements.get(i));
            m = P256Group.scalarMultiply(blindedElements.get(i), coefficient).add(m);
        }
        m = m.normalize();

        return new Composites(m, P256Group.scalarMultiply(m, secretKey));
    }

    /** ComputeComposites: the verifier's variant, which has no access to k. */
    private static Composites computeComposites(ECPoint publicKey,
                                                List<ECPoint> blindedElements,
                                                List<ECPoint> evaluatedElements) {
        byte[] seed = compositeSeed(publicKey);

        ECPoint m = P256Group.identity();
        ECPoint z = P256Group.identity();
        for (int i = 0; i < blindedElements.size(); i++) {
            BigInteger coefficient = compositeCoefficient(seed, i, blindedElements.get(i), evaluatedElements.get(i));
            m = P256Group.scalarMultiply(blindedElements.get(i), coefficient).add(m);
            z = P256Group.scalarMultiply(evaluatedElements.get(i), coefficient).add(z);
        }

        return new Composites(m.normalize(), z.normalize());
    }

    private static byte[] compositeSeed(ECPoint publicKey) {
        byte[] serializedPublicKey = P256Group.serializeElement(publicKey);
        return Oprf.hash(Oprf.concat(
                Oprf.lengthPrefixed(serializedPublicKey),
                Oprf.lengthPrefixed(Oprf.DST_SEED)
        ));
    }

    /**
     * The per-element coefficient d_i. Binding it to the index and to both
     * elements of the pair is what stops a dishonest server from reordering or
     * swapping entries within a batch.
     */
    private static BigInteger compositeCoefficient(byte[] seed, int index, ECPoint blinded, ECPoint evaluated) {
        byte[] transcript = Oprf.concat(
                Oprf.lengthPrefixed(seed),
                Oprf.i2osp2(index),
                Oprf.lengthPrefixed(P256Group.serializeElement(blinded)),
                Oprf.lengthPrefixed(P256Group.serializeElement(evaluated)),
                Oprf.LABEL_COMPOSITE
        );
        return P256Group.hashToScalar(transcript);
    }

    /** The Fiat-Shamir challenge, computed identically by prover and verifier. */
    private static BigInteger challenge(ECPoint publicKey, ECPoint m, ECPoint z, ECPoint t2, ECPoint t3) {
        byte[] transcript = Oprf.concat(
                Oprf.lengthPrefixed(P256Group.serializeElement(publicKey)),
                Oprf.lengthPrefixed(P256Group.serializeElement(m)),
                Oprf.lengthPrefixed(P256Group.serializeElement(z)),
                Oprf.lengthPrefixed(P256Group.serializeElement(t2)),
                Oprf.lengthPrefixed(P256Group.serializeElement(t3)),
                Oprf.LABEL_CHALLENGE
        );
        return P256Group.hashToScalar(transcript);
    }

    private static void requireMatchingLists(List<ECPoint> blindedElements, List<ECPoint> evaluatedElements) {
        if (blindedElements.isEmpty()) {
            throw new OprfProtocolException("a DLEQ proof requires at least one element");
        }
        if (blindedElements.size() != evaluatedElements.size()) {
            throw new OprfProtocolException("blinded and evaluated element counts differ");
        }
    }

    private record Composites(ECPoint m, ECPoint z) {
    }
}
