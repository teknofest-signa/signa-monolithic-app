package teknofest.signa.producer.crypto;

import java.math.BigInteger;
import java.util.List;
import org.bouncycastle.math.ec.ECPoint;

/**
 * The client half of the VOPRF, RFC 9497 Section 3.3.2 — the code that belongs
 * to a <b>member bank</b>, not to the central server.
 *
 * <p>It lives in this repository so that the reference implementation, the test
 * vectors and the server stay in one place and cannot drift apart. A real
 * deployment ships this class (or a port of it) inside each bank's connector,
 * where the customer identifier never leaves the bank's own perimeter.
 *
 * <p>The full flow for one identifier:
 * <ol>
 *   <li>{@link #blind(byte[])} maps the identifier to a curve point and
 *       multiplies it by a fresh random scalar r. The result carries no
 *       recoverable information about the identifier.</li>
 *   <li>The bank sends the blinded point to the server, which returns
 *       {@code k * (r * P)} together with a DLEQ proof.</li>
 *   <li>{@link #finalize(byte[], BigInteger, ECPoint, ECPoint, ECPoint, DleqProof)}
 *       verifies the proof, multiplies by the modular inverse of r to strip the
 *       blind, leaving {@code k * P}, and hashes it into the pseudonym.</li>
 * </ol>
 *
 * <p>The blind r must be fresh per evaluation. Reusing one across two
 * identifiers would let the server test whether the two are equal by comparing
 * the blinded points directly.
 */
public final class VoprfClient {

    private VoprfClient() {
    }

    /** Blind, RFC 9497 Section 3.3.1. */
    public static BlindResult blind(byte[] input) {
        ECPoint inputElement = P256Group.hashToGroup(input);
        if (inputElement.isInfinity()) {
            throw new OprfProtocolException("input maps to the identity element");
        }
        BigInteger blind = P256Group.randomScalar();

        return new BlindResult(blind, P256Group.scalarMultiply(inputElement, blind));
    }

    /** Deterministic-blind variant, used only to replay the RFC test vectors. */
    static BlindResult blind(byte[] input, BigInteger blind) {
        ECPoint inputElement = P256Group.hashToGroup(input);
        return new BlindResult(blind, P256Group.scalarMultiply(inputElement, blind));
    }

    /**
     * Finalize for the verifiable mode: check the proof first, then unblind.
     *
     * <p>The proof check is not a formality. Skipping it would let a malicious
     * or compromised server return an element computed under a key of its own
     * choosing, silently partitioning that bank's pseudonyms away from every
     * other bank's while remaining able to link them itself.
     *
     * @throws OprfProtocolException if the DLEQ proof does not verify
     */
    public static byte[] finalize(byte[] input,
                                  BigInteger blind,
                                  ECPoint evaluatedElement,
                                  ECPoint blindedElement,
                                  ECPoint publicKey,
                                  DleqProof proof) {
        boolean verified = Dleq.verifyProof(
                publicKey, List.of(blindedElement), List.of(evaluatedElement), proof);
        if (!verified) {
            throw new OprfProtocolException("the server's DLEQ proof did not verify");
        }
        return unblindAndHash(input, blind, evaluatedElement);
    }

    /**
     * Batched Finalize: verifies one proof covering the whole batch, then
     * unblinds every element. All-or-nothing by design — a batch with one bad
     * element is rejected whole, because a partial accept would let a server
     * probe which elements a bank is willing to drop.
     */
    public static byte[][] finalizeBatch(byte[][] inputs,
                                         BigInteger[] blinds,
                                         List<ECPoint> evaluatedElements,
                                         List<ECPoint> blindedElements,
                                         ECPoint publicKey,
                                         DleqProof proof) {
        if (inputs.length != blinds.length || inputs.length != evaluatedElements.size()) {
            throw new OprfProtocolException("batch sizes do not line up");
        }
        boolean verified = Dleq.verifyProof(publicKey, blindedElements, evaluatedElements, proof);
        if (!verified) {
            throw new OprfProtocolException("the server's DLEQ proof did not verify");
        }

        byte[][] outputs = new byte[inputs.length][];
        for (int i = 0; i < inputs.length; i++) {
            outputs[i] = unblindAndHash(inputs[i], blinds[i], evaluatedElements.get(i));
        }
        return outputs;
    }

    /**
     * N = r^-1 * E, then output = Hash(len(input) || input || len(N) || N || "Finalize").
     *
     * <p>Binding the input back into the final hash is what keeps the output a
     * pseudorandom function of the identifier rather than merely an encoding of
     * the curve point.
     */
    private static byte[] unblindAndHash(byte[] input, BigInteger blind, ECPoint evaluatedElement) {
        ECPoint unblinded = P256Group.scalarMultiply(evaluatedElement, P256Group.scalarInverse(blind));

        return Oprf.hash(Oprf.concat(
                Oprf.lengthPrefixed(input),
                Oprf.lengthPrefixed(P256Group.serializeElement(unblinded)),
                Oprf.LABEL_FINALIZE
        ));
    }

    public record BlindResult(BigInteger blind, ECPoint blindedElement) {
    }
}
