package teknofest.signa.producer.crypto;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Ciphersuite constants and byte-level primitives shared by every part of the
 * OPRF layer, as specified in RFC 9497 ("Oblivious Pseudorandom Functions
 * (OPRFs) Using Prime-Order Groups").
 *
 * <p>SIGNA runs the <b>VOPRF</b> variant (mode 0x01) of ciphersuite
 * {@code P256-SHA256}. Verifiability is not optional here: the DLEQ proof the
 * server returns is what stops the central server from silently using a
 * different key per bank, which would let it partition the pseudonym space and
 * de-anonymise customers by correlation.
 *
 * <p>Every hash in this layer is domain separated by {@link #CONTEXT_STRING}.
 * Two deployments with different context strings can never produce colliding
 * pseudonyms, and a value produced for one protocol mode cannot be replayed
 * into another.
 */
public final class Oprf {

    private Oprf() {
    }

    /** Ciphersuite identifier, RFC 9497 Section 4.3. */
    public static final String CIPHERSUITE_IDENTIFIER = "P256-SHA256";

    /** Protocol version prefix, RFC 9497 Section 3.1. */
    private static final String VERSION_PREFIX = "OPRFV1-";

    /** modeVOPRF, RFC 9497 Section 3.1 Table 1. */
    public static final byte MODE_VOPRF = 0x01;

    /**
     * contextString = "OPRFV1-" || I2OSP(mode, 1) || "-" || identifier.
     */
    public static final byte[] CONTEXT_STRING = concat(
            ascii(VERSION_PREFIX),
            new byte[]{MODE_VOPRF},
            ascii("-"),
            ascii(CIPHERSUITE_IDENTIFIER)
    );

    public static final byte[] DST_HASH_TO_GROUP = concat(ascii("HashToGroup-"), CONTEXT_STRING);
    public static final byte[] DST_HASH_TO_SCALAR = concat(ascii("HashToScalar-"), CONTEXT_STRING);
    public static final byte[] DST_DERIVE_KEY_PAIR = concat(ascii("DeriveKeyPair"), CONTEXT_STRING);
    public static final byte[] DST_SEED = concat(ascii("Seed-"), CONTEXT_STRING);

    public static final byte[] LABEL_COMPOSITE = ascii("Composite");
    public static final byte[] LABEL_CHALLENGE = ascii("Challenge");
    public static final byte[] LABEL_FINALIZE = ascii("Finalize");

    /** Output length of the ciphersuite hash (SHA-256), in bytes. Nh. */
    public static final int HASH_LENGTH = 32;

    public static byte[] hash(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JRE specification", exception);
        }
    }

    public static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    /** I2OSP(value, 1) — one-byte big-endian encoding. */
    public static byte[] i2osp1(int value) {
        if (value < 0 || value > 0xFF) {
            throw new IllegalArgumentException("value does not fit in one octet");
        }
        return new byte[]{(byte) value};
    }

    /** I2OSP(value, 2) — two-byte big-endian encoding. */
    public static byte[] i2osp2(int value) {
        if (value < 0 || value > 0xFFFF) {
            throw new IllegalArgumentException("value does not fit in two octets");
        }
        return new byte[]{(byte) (value >>> 8), (byte) value};
    }

    /**
     * Length-prefixed concatenation, written in the transcripts as
     * {@code I2OSP(len(x), 2) || x}. The prefix is what makes the transcript
     * unambiguous: without it an attacker could shift bytes between adjacent
     * fields and produce a second input that hashes identically.
     */
    public static byte[] lengthPrefixed(byte[] value) {
        return concat(i2osp2(value.length), value);
    }

    public static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.write(part, 0, part.length);
        }
        return out.toByteArray();
    }

    /**
     * Compares two byte arrays without leaking, through timing, how many
     * leading bytes matched. Used for every secret comparison in this layer.
     */
    public static boolean constantTimeEquals(byte[] left, byte[] right) {
        return MessageDigest.isEqual(left, right);
    }

    public static String toHex(byte[] value) {
        StringBuilder builder = new StringBuilder(value.length * 2);
        for (byte b : value) {
            builder.append(Character.forDigit((b >> 4) & 0xF, 16));
            builder.append(Character.forDigit(b & 0xF, 16));
        }
        return builder.toString();
    }

    public static byte[] fromHex(String value) {
        String normalized = value.trim();
        if (normalized.length() % 2 != 0) {
            throw new IllegalArgumentException("hex string must have an even length");
        }
        byte[] out = new byte[normalized.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int high = Character.digit(normalized.charAt(i * 2), 16);
            int low = Character.digit(normalized.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("hex string contains a non-hex character");
            }
            out[i] = (byte) ((high << 4) | low);
        }
        return out;
    }
}
