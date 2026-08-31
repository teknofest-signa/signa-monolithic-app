package teknofest.signa.producer.crypto;

import java.math.BigInteger;
import java.security.SecureRandom;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.BigIntegers;

/**
 * The prime-order group used by the SIGNA OPRF layer: NIST P-256 (secp256r1),
 * as required by ciphersuite {@code P256-SHA256} in RFC 9497 Section 4.3.
 *
 * <p>Point arithmetic and SEC1 decoding are delegated to Bouncy Castle rather
 * than hand-rolled. Point decoding in particular is where OPRF servers get
 * attacked: a caller who can get the server to multiply its secret key by a
 * point that is not on the curve, or that lies in a small subgroup, can recover
 * the key one residue at a time. {@link #decodeElement(byte[])} is the single
 * gate through which every caller-supplied point passes.
 */
public final class P256Group {

    private P256Group() {
    }

    private static final X9ECParameters PARAMETERS = CustomNamedCurves.getByName("secp256r1");

    public static final ECCurve CURVE = PARAMETERS.getCurve();
    public static final ECPoint GENERATOR = PARAMETERS.getG();

    /** Group order, RFC 9497 Section 4.3. */
    public static final BigInteger ORDER = PARAMETERS.getN();

    public static final BigInteger FIELD_PRIME = CURVE.getField().getCharacteristic();

    /** Ne: serialised element length, SEC1 compressed form. */
    public static final int ELEMENT_LENGTH = 33;

    /** Ns: serialised scalar length. */
    public static final int SCALAR_LENGTH = 32;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static ECPoint identity() {
        return CURVE.getInfinity();
    }

    public static ECPoint createPoint(BigInteger x, BigInteger y) {
        return CURVE.createPoint(x, y);
    }

    /**
     * SerializeElement, RFC 9497 Section 4.3: SEC1 compressed encoding.
     */
    public static byte[] serializeElement(ECPoint element) {
        if (element.isInfinity()) {
            throw new IllegalArgumentException("the identity element has no valid serialisation here");
        }
        return element.normalize().getEncoded(true);
    }

    /**
     * DeserializeElement, RFC 9497 Section 4.3, with the full set of validation
     * checks an OPRF server needs before touching the input with its key.
     *
     * <p>Rejects, in order: a wrong-length encoding, an uncompressed or
     * malformed prefix, an x-coordinate with no corresponding curve point, the
     * identity element, and any point failing Bouncy Castle's on-curve and
     * subgroup validation. The identity check matters specifically: evaluating
     * the identity returns the identity regardless of the key, which a caller
     * could use to probe whether the server is answering honestly, and it is
     * also the degenerate input the RFC calls out.
     *
     * @throws OprfProtocolException if the input is not a valid group element
     */
    public static ECPoint decodeElement(byte[] encoded) {
        if (encoded == null || encoded.length != ELEMENT_LENGTH) {
            throw new OprfProtocolException("element must be " + ELEMENT_LENGTH + " bytes in SEC1 compressed form");
        }
        if (encoded[0] != 0x02 && encoded[0] != 0x03) {
            throw new OprfProtocolException("element must use a compressed SEC1 prefix (0x02 or 0x03)");
        }

        ECPoint point;
        try {
            point = CURVE.decodePoint(encoded);
        } catch (IllegalArgumentException exception) {
            throw new OprfProtocolException("element is not a point on P-256");
        }

        if (point.isInfinity()) {
            throw new OprfProtocolException("element must not be the identity");
        }
        // isValid() re-checks the curve equation and, for curves with a cofactor,
        // subgroup membership. P-256 has cofactor 1, so on-curve implies correct
        // order, but the check is kept so the guarantee survives a curve change.
        if (!point.isValid()) {
            throw new OprfProtocolException("element failed group validation");
        }
        return point.normalize();
    }

    /** SerializeScalar: fixed-width 32-byte big-endian encoding. */
    public static byte[] serializeScalar(BigInteger scalar) {
        return BigIntegers.asUnsignedByteArray(SCALAR_LENGTH, scalar);
    }

    /**
     * DeserializeScalar, rejecting anything outside [0, order - 1]. A
     * fixed-width encoding is required so that a scalar has exactly one valid
     * representation and proof bytes cannot be malleated.
     */
    public static BigInteger decodeScalar(byte[] encoded) {
        if (encoded == null || encoded.length != SCALAR_LENGTH) {
            throw new OprfProtocolException("scalar must be " + SCALAR_LENGTH + " bytes");
        }
        BigInteger scalar = new BigInteger(1, encoded);
        if (scalar.compareTo(ORDER) >= 0) {
            throw new OprfProtocolException("scalar is not reduced modulo the group order");
        }
        return scalar;
    }

    /**
     * RandomScalar, drawn uniformly from [1, order - 1]. Zero is excluded: a
     * zero blind would send the identity to the server and destroy the
     * blinding, and a zero proof nonce would reveal the secret key.
     */
    public static BigInteger randomScalar() {
        return BigIntegers.createRandomInRange(BigInteger.ONE, ORDER.subtract(BigInteger.ONE), SECURE_RANDOM);
    }

    /**
     * HashToScalar, RFC 9497 Section 4.3: expand_message_xmd with SHA-256 and
     * L = 48, reduced modulo the group order. The 48-byte intermediate is what
     * keeps the reduction bias below the security level.
     */
    public static BigInteger hashToScalar(byte[] input, byte[] domainSeparationTag) {
        byte[] uniformBytes = HashToCurve.expandMessageXmd(input, domainSeparationTag, 48);
        return new BigInteger(1, uniformBytes).mod(ORDER);
    }

    public static BigInteger hashToScalar(byte[] input) {
        return hashToScalar(input, Oprf.DST_HASH_TO_SCALAR);
    }

    /** HashToGroup, RFC 9497 Section 4.3. */
    public static ECPoint hashToGroup(byte[] input) {
        return HashToCurve.hashToGroup(input, Oprf.DST_HASH_TO_GROUP);
    }

    public static ECPoint scalarMultiplyGenerator(BigInteger scalar) {
        return GENERATOR.multiply(scalar).normalize();
    }

    public static ECPoint scalarMultiply(ECPoint point, BigInteger scalar) {
        return point.multiply(scalar).normalize();
    }

    public static BigInteger scalarInverse(BigInteger scalar) {
        return scalar.modInverse(ORDER);
    }
}
