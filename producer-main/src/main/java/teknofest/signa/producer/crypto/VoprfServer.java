package teknofest.signa.producer.crypto;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.bouncycastle.math.ec.ECPoint;

/**
 * The server half of the VOPRF, RFC 9497 Section 3.3.2.
 *
 * <p>Everything this class does is deliberately blind. It receives points, it
 * multiplies them by the key, it proves it used the right key. It never sees a
 * customer identifier, and there is no code path here through which one could
 * arrive: the only inputs are group elements.
 *
 * <p>This class holds no state and no key material. Keys are supplied per call
 * by {@link teknofest.signa.producer.crypto.key.OprfKeyRing}, which keeps the
 * option of moving them behind an HSM or KMS open.
 */
public final class VoprfServer {

    private VoprfServer() {
    }

    /**
     * BlindEvaluate over a batch: each element is multiplied by the key, and a
     * single batched DLEQ proof covers the whole list.
     */
    public static BlindEvaluateResult blindEvaluate(BigInteger secretKey,
                                                    ECPoint publicKey,
                                                    List<ECPoint> blindedElements) {
        List<ECPoint> evaluatedElements = new ArrayList<>(blindedElements.size());
        for (ECPoint blindedElement : blindedElements) {
            evaluatedElements.add(P256Group.scalarMultiply(blindedElement, secretKey));
        }

        DleqProof proof = Dleq.generateProof(secretKey, publicKey, blindedElements, evaluatedElements);

        return new BlindEvaluateResult(evaluatedElements, proof);
    }

    /**
     * DeriveKeyPair, RFC 9497 Section 3.2.1: deterministic key generation from
     * a 32-byte seed and a public info string.
     *
     * <p>Used for reproducible development keys and for key ceremonies where
     * the seed, not the scalar, is what gets escrowed.
     */
    public static BigInteger deriveSecretKey(byte[] seed, byte[] info) {
        if (seed == null || seed.length != 32) {
            throw new IllegalArgumentException("DeriveKeyPair requires a 32-byte seed");
        }
        byte[] deriveInput = Oprf.concat(seed, Oprf.lengthPrefixed(info));

        for (int counter = 0; counter <= 255; counter++) {
            BigInteger candidate = P256Group.hashToScalar(
                    Oprf.concat(deriveInput, Oprf.i2osp1(counter)),
                    Oprf.DST_DERIVE_KEY_PAIR);
            if (candidate.signum() != 0) {
                return candidate;
            }
        }
        throw new IllegalStateException("DeriveKeyPair exhausted its counter");
    }

    /**
     * The PRF evaluated directly by a party that knows both the key and the
     * input, RFC 9497 Section 3.3.1.
     *
     * <p>Present for one reason: it is the definition the blinded path must
     * agree with, and the tests assert that agreement. It is never reachable
     * from a request handler, because the server never holds an input.
     */
    public static byte[] evaluate(BigInteger secretKey, byte[] input) {
        ECPoint inputElement = P256Group.hashToGroup(input);
        if (inputElement.isInfinity()) {
            throw new OprfProtocolException("input maps to the identity element");
        }
        ECPoint evaluatedElement = P256Group.scalarMultiply(inputElement, secretKey);

        return Oprf.hash(Oprf.concat(
                Oprf.lengthPrefixed(input),
                Oprf.lengthPrefixed(P256Group.serializeElement(evaluatedElement)),
                Oprf.LABEL_FINALIZE
        ));
    }

    public record BlindEvaluateResult(List<ECPoint> evaluatedElements, DleqProof proof) {
    }
}
