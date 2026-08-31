package teknofest.signa.producer.handler;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.util.HtmlUtils;
import teknofest.signa.producer.model.dto.shared.ErrorResponse;
import teknofest.signa.producer.handler.exception.ApplicationException;
import teknofest.signa.producer.handler.exception.ExternalServiceException;
import teknofest.signa.producer.handler.exception.InvalidCredentialsException;
import teknofest.signa.producer.handler.exception.ResourceNotFoundException;
import tools.jackson.databind.exc.InvalidFormatException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.debug("Resource not found: {}", ex.getMessage());
        return buildErrorResponse(ex, HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
        log.debug("Bad request: {}", ex.getMessage());
        return buildErrorResponse(ex, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(AccessDeniedException ex, HttpServletRequest request) {
        log.debug("Access denied: {}", ex.getMessage());
        return buildErrorResponse(ex, HttpStatus.FORBIDDEN, request);
    }

    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ErrorResponse> handleExternalService(ExternalServiceException ex, HttpServletRequest request) {
        log.error("External service error: {}", ex.getMessage(), ex);
        return buildErrorResponse(ex, HttpStatus.SERVICE_UNAVAILABLE, request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(Exception ex, HttpServletRequest request) {
        log.debug("Authentication failed: {}", ex.getMessage());
        return buildErrorResponse(ex, HttpStatus.UNAUTHORIZED, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.debug("Message not readable: {}", ex.getMessage());

        if (isInvalidEnumValue(ex)) {
            return buildErrorResponse(
                    new IllegalArgumentException("Invalid enum value provided!"),
                    HttpStatus.BAD_REQUEST,
                    request
            );
        }
        return buildErrorResponse(ex, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.debug("Validation failed: {}", ex.getMessage());

        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> HtmlUtils.htmlEscape(Objects.nonNull(error.getDefaultMessage())
                                ? error.getDefaultMessage()
                                : "Invalid value!"),
                        (existing, _) -> existing
                ));

        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST)
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .timestamp(LocalDateTime.now())
                .message("Validation failed")
                .path(sanitizePath(request.getRequestURI()))
                .errors(errors)
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error occurred: ", ex);
        Exception safeException = new RuntimeException("An unexpected error occurred");
        return buildErrorResponse(safeException, HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(Exception ex, HttpStatus status, HttpServletRequest request) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(status)
                .statusCode(status.value())
                .timestamp(LocalDateTime.now())
                .message(HtmlUtils.htmlEscape(ex.getMessage()))
                .path(sanitizePath(request.getRequestURI()))
                .build();

        return new ResponseEntity<>(errorResponse, status);
    }

    private boolean isInvalidEnumValue(HttpMessageNotReadableException ex) {
        return ex.getCause() instanceof InvalidFormatException ife && ife.getTargetType().isEnum();
    }

    private String sanitizePath(String path) {
        return HtmlUtils.htmlEscape(path);
    }
}
