package teknofest.signa.producer.crypto;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.bouncycastle.math.ec.ECPoint;

/**
 * RFC 9380 hash-to-curve for suite {@code P256_XMD:SHA-256_SSWU_RO_}.
 *
 * <p>This is the hash-to-curve step the bank applies to a customer identifier
 * before blinding it. Hashing to a curve point rather than to a plain digest is
 * what makes the rest of the protocol possible: the resulting point can be
 * multiplied by a blinding scalar, sent to a server that has no way to reverse
 * the blinding, and unblinded afterwards.
 *
 * <p>Hashing repeatedly until a digest happens to be a valid x-coordinate would
 * leak the input through the number of iterations. The Simplified SWU map used
 * here always succeeds in a single pass, so the control flow does not depend on
 * the input.
 *
 * <p><b>Side-channel note.</b> The field arithmetic below uses
 * {@link BigInteger}, which is not constant time. On the server this does not
 * matter: the server never calls this function, it only multiplies a blinded
 * point it received. On the bank side the input is the customer identifier, so
 * a production connector running large enrolment batches on shared hardware
 * should move to a constant-time field implementation. See
 * {@code docs/oprf-layer.md}.
 */
public final class HashToCurve {

    private HashToCurve() {
    }

    private static final BigInteger P = P256Group.FIELD_PRIME;
    private static final BigInteger A = P.subtract(BigInteger.valueOf(3));
    private static final BigInteger B = new BigInteger(
            "5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16);

    /** Z = -10, RFC 9380 Section 8.2. */
    private static final BigInteger Z = P.subtract(BigInteger.valueOf(10));

    /** p = 3 (mod 4) for P-256, so sqrt(a) = a^((p+1)/4). */
    private static final BigInteger SQRT_EXPONENT = P.add(BigInteger.ONE).shiftRight(2);

    /** L for P-256, RFC 9380 Section 8.2: 48 bytes per field element. */
    private static final int FIELD_ELEMENT_BYTES = 48;

    private static final int SHA256_OUTPUT_BYTES = 32;
    private static final int SHA256_BLOCK_BYTES = 64;

    /**
     * hash_to_curve: maps an arbitrary byte string to a point of the
     * prime-order group, indifferentiable from a random oracle.
     */
    public static ECPoint hashToGroup(byte[] message, byte[] domainSeparationTag) {
        BigInteger[] u = hashToField(message, domainSeparationTag, 2);
        ECPoint q0 = mapToCurveSimpleSwu(u[0]);
        ECPoint q1 = mapToCurveSimpleSwu(u[1]);
        // P-256 has cofactor 1, so clear_cofactor is the identity map.
        return q0.add(q1).normalize();
    }

    /** hash_to_field, RFC 9380 Section 5.2, for a prime field with m = 1. */
    static BigInteger[] hashToField(byte[] message, byte[] domainSeparationTag, int count) {
        byte[] uniformBytes = expandMessageXmd(message, domainSeparationTag, count * FIELD_ELEMENT_BYTES);
        BigInteger[] elements = new BigInteger[count];
        for (int i = 0; i < count; i++) {
            byte[] chunk = new byte[FIELD_ELEMENT_BYTES];
            System.arraycopy(uniformBytes, i * FIELD_ELEMENT_BYTES, chunk, 0, FIELD_ELEMENT_BYTES);
            elements[i] = new BigInteger(1, chunk).mod(P);
        }
        return elements;
    }

    /**
     * expand_message_xmd with SHA-256, RFC 9380 Section 5.3.1. Takes the output
     * length as a parameter because hash-to-scalar reuses it with a different
     * length than hash-to-field does.
     */
    static byte[] expandMessageXmd(byte[] message, byte[] domainSeparationTag, int lengthInBytes) {
        if (domainSeparationTag.length > 255) {
            throw new IllegalArgumentException("domain separation tag exceeds 255 bytes");
        }
        int ell = (lengthInBytes + SHA256_OUTPUT_BYTES - 1) / SHA256_OUTPUT_BYTES;
        if (ell > 255 || lengthInBytes > 65535) {
            throw new IllegalArgumentException("requested expansion length is out of range");
        }

        byte[] dstPrime = Oprf.concat(domainSeparationTag, Oprf.i2osp1(domainSeparationTag.length));
        byte[] zPad = new byte[SHA256_BLOCK_BYTES];

        // b0 = H(Z_pad || msg || I2OSP(len, 2) || I2OSP(0, 1) || DST_prime)
        byte[] b0 = sha256().digest(Oprf.concat(
                zPad, message, Oprf.i2osp2(lengthInBytes), Oprf.i2osp1(0), dstPrime));

        byte[][] blocks = new byte[ell][];
        blocks[0] = sha256().digest(Oprf.concat(b0, Oprf.i2osp1(1), dstPrime));
        for (int i = 2; i <= ell; i++) {
            byte[] xored = new byte[SHA256_OUTPUT_BYTES];
            for (int j = 0; j < SHA256_OUTPUT_BYTES; j++) {
                xored[j] = (byte) (b0[j] ^ blocks[i - 2][j]);
            }
            blocks[i - 1] = sha256().digest(Oprf.concat(xored, Oprf.i2osp1(i), dstPrime));
        }

        byte[] uniformBytes = new byte[lengthInBytes];
        for (int i = 0; i < ell; i++) {
            int offset = i * SHA256_OUTPUT_BYTES;
            int length = Math.min(SHA256_OUTPUT_BYTES, lengthInBytes - offset);
            System.arraycopy(blocks[i], 0, uniformBytes, offset, length);
        }
        return uniformBytes;
    }

    /**
     * map_to_curve_simple_swu, RFC 9380 Section 6.6.2, for a curve with
     * A != 0 and B != 0, which P-256 satisfies.
     */
    private static ECPoint mapToCurveSimpleSwu(BigInteger u) {
        BigInteger uSquared = mul(u, u);
        BigInteger zuSquared = mul(Z, uSquared);
        BigInteger zSquaredU4 = mul(zuSquared, zuSquared);

        // tv1 = inv0(Z^2 * u^4 + Z * u^2); inv0(0) is defined as 0.
        BigInteger tv1 = inv0(add(zSquaredU4, zuSquared));

        BigInteger x1;
        if (tv1.signum() == 0) {
            // Exceptional case. The choice of Z guarantees g(B / (Z * A)) is square.
            x1 = mul(B, inv0(mul(Z, A)));
        } else {
            x1 = mul(mul(P.subtract(B), inv0(A)), add(BigInteger.ONE, tv1));
        }

        BigInteger gx1 = curveEquation(x1);
        BigInteger x2 = mul(zuSquared, x1);

        BigInteger x;
        BigInteger y;
        BigInteger candidateY = sqrt(gx1);
        if (mul(candidateY, candidateY).equals(gx1)) {
            x = x1;
            y = candidateY;
        } else {
            x = x2;
            y = sqrt(curveEquation(x2));
        }

        // Fix the sign of y so that sgn0(y) == sgn0(u).
        if (sgn0(u) != sgn0(y)) {
            y = P.subtract(y);
        }

        return P256Group.createPoint(x, y);
    }

    /** g(x) = x^3 + A*x + B over F_p. */
    private static BigInteger curveEquation(BigInteger x) {
        return add(add(mul(mul(x, x), x), mul(A, x)), B);
    }

    /** sgn0 for m = 1, RFC 9380 Section 4.1: the least significant bit. */
    private static int sgn0(BigInteger value) {
        return value.testBit(0) ? 1 : 0;
    }

    private static BigInteger sqrt(BigInteger value) {
        return value.modPow(SQRT_EXPONENT, P);
    }

    /** inv0(x) = x^(p-2), which yields 0 for x = 0 as the specification requires. */
    private static BigInteger inv0(BigInteger value) {
        return value.modPow(P.subtract(BigInteger.TWO), P);
    }

    private static BigInteger mul(BigInteger left, BigInteger right) {
        return left.multiply(right).mod(P);
    }

    private static BigInteger add(BigInteger left, BigInteger right) {
        return left.add(right).mod(P);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JRE specification", exception);
        }
    }
}
