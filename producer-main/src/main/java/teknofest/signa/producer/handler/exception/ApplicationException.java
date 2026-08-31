package teknofest.signa.producer.handler.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ApplicationException extends RuntimeException {
    public ApplicationException(String message) {
        super(message);
    }
}
