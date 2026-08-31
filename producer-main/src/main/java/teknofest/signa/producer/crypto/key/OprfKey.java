package teknofest.signa.producer.crypto.key;

import java.math.BigInteger;
import org.bouncycastle.math.ec.ECPoint;
import teknofest.signa.producer.crypto.Oprf;
import teknofest.signa.producer.crypto.P256Group;

/**
 * One OPRF server key: the secret scalar k, its public counterpart
 * {@code pkS = k * G}, and a key identifier.
 *
 * <p>The identifier is <b>derived from the public key</b> rather than
 * configured by hand. That is deliberate. Pseudonyms are only comparable when
 * they were produced under the same key, so every stored pseudonym carries the
 * identifier of the key that produced it. If an operator could type the
 * identifier in freely, a configuration slip could label two different keys the
 * same, and the platform would start comparing pseudonyms that can never match
 * — a silent failure that looks exactly like "this person banks nowhere else".
 * Deriving it from the key makes that impossible, and lets a bank pin the
 * identifier it expects.
 */
public record OprfKey(String keyId, BigInteger secretKey, ECPoint publicKey) {

    private static final int KEY_ID_HEX_LENGTH = 16;

    public static OprfKey fromSecret(BigInteger secretKey) {
        if (secretKey == null || secretKey.signum() <= 0 || secretKey.compareTo(P256Group.ORDER) >= 0) {
            throw new IllegalArgumentException("OPRF secret key must be a scalar in [1, order - 1]");
        }
        ECPoint publicKey = P256Group.scalarMultiplyGenerator(secretKey);

        return new OprfKey(deriveKeyId(publicKey), secretKey, publicKey);
    }

    /** keyId = the first 8 bytes of SHA-256 over the serialised public key. */
    private static String deriveKeyId(ECPoint publicKey) {
        byte[] digest = Oprf.hash(P256Group.serializeElement(publicKey));
        return Oprf.toHex(digest).substring(0, KEY_ID_HEX_LENGTH);
    }

    public String publicKeyHex() {
        return Oprf.toHex(P256Group.serializeElement(publicKey));
    }

    /**
     * Keeps the secret out of logs, stack traces and actuator dumps. Records
     * generate a {@code toString} that prints every component, which for this
     * type would print the private key.
     */
    @Override
    public String toString() {
        return "OprfKey[keyId=" + keyId + ", publicKey=" + publicKeyHex() + "]";
    }
}
