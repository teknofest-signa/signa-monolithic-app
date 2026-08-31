package teknofest.signa.producer.crypto;

import java.math.BigInteger;

/**
 * A non-interactive zero-knowledge proof of discrete logarithm equality
 * (Chaum-Pedersen, made non-interactive by Fiat-Shamir), serialised as the pair
 * of scalars {@code (c, s)} per RFC 9497 Section 2.2.
 *
 * <p>This is the zero-knowledge component of the SIGNA privacy layer. It proves
 * the statement "the key that maps the generator to the published public key is
 * the same key that mapped your blinded element to the value I returned",
 * without revealing that key. Without it, the central server could hand each
 * bank a different key, which would make every bank's pseudonyms unlinkable to
 * the others while leaving the server able to link all of them — quietly
 * turning the whole platform into a one-way surveillance channel.
 */
public record DleqProof(BigInteger challenge, BigInteger response) {

    public static final int SERIALIZED_LENGTH = 2 * P256Group.SCALAR_LENGTH;

    /** proof = SerializeScalar(c) || SerializeScalar(s). */
    public byte[] serialize() {
        return Oprf.concat(
                P256Group.serializeScalar(challenge),
                P256Group.serializeScalar(response)
        );
    }

    public static DleqProof deserialize(byte[] encoded) {
        if (encoded == null || encoded.length != SERIALIZED_LENGTH) {
            throw new OprfProtocolException("proof must be " + SERIALIZED_LENGTH + " bytes");
        }
        byte[] challengeBytes = new byte[P256Group.SCALAR_LENGTH];
        byte[] responseBytes = new byte[P256Group.SCALAR_LENGTH];
        System.arraycopy(encoded, 0, challengeBytes, 0, P256Group.SCALAR_LENGTH);
        System.arraycopy(encoded, P256Group.SCALAR_LENGTH, responseBytes, 0, P256Group.SCALAR_LENGTH);

        return new DleqProof(P256Group.decodeScalar(challengeBytes), P256Group.decodeScalar(responseBytes));
    }

    public String toHex() {
        return Oprf.toHex(serialize());
    }

    public static DleqProof fromHex(String hex) {
        return deserialize(Oprf.fromHex(hex));
    }
}
