package teknofest.signa.producer.crypto;

import teknofest.signa.producer.handler.exception.ApplicationException;

/**
 * Raised when a value crossing the OPRF trust boundary is malformed or fails a
 * validation check: a point that is not on the curve, an unreduced scalar, a
 * proof that does not verify.
 *
 * <p>Extends {@link ApplicationException} so the existing handler maps it to
 * 400 rather than 500. The message is deliberately generic about which check
 * failed on the caller's specific input, so that a caller probing the server
 * cannot use error text as an oracle.
 */
public class OprfProtocolException extends ApplicationException {

    public OprfProtocolException(String message) {
        super(message);
    }
}
