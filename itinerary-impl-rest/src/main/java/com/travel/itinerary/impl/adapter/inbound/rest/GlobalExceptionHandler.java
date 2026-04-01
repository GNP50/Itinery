package com.travel.itinerary.impl.adapter.inbound.rest;

import com.travel.itinerary.impl.exception.AuthenticationRequiredException;
import com.travel.itinerary.impl.exception.ExternalServiceException;
import com.travel.itinerary.impl.exception.InvalidTokenException;
import com.travel.itinerary.impl.exception.ItineraryNotFoundException;
import com.travel.itinerary.impl.exception.QueueFullException;
import com.travel.itinerary.impl.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Centralised exception-to-HTTP-response mapping for all REST controllers
 * in the {@code itinerary-impl} module.
 *
 * <h3>Error body format</h3>
 * Every error response uses the sealed {@link ErrorResponse} record so that
 * clients can rely on a consistent JSON shape regardless of which exception
 * was raised:
 * <pre>
 * {
 *   "status":    404,
 *   "error":     "Not Found",
 *   "message":   "Itinerary not found: 3fa85f64-...",
 *   "path":      "/api/v1/itineraries/3fa85f64-...",
 *   "timestamp": "2024-06-01T12:00:00.000Z"
 * }
 * </pre>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // -------------------------------------------------------------------------
    // Error response record (Java 21)
    // -------------------------------------------------------------------------

    /**
     * Immutable, JSON-serialisable error response body.
     *
     * @param status    HTTP status code
     * @param error     HTTP status reason phrase
     * @param message   detailed, human-readable error message
     * @param path      request URI that triggered the error
     * @param timestamp instant at which the error was generated
     */
    public record ErrorResponse(
            int     status,
            String  error,
            String  message,
            String  path,
            Instant timestamp
    ) {}

    // -------------------------------------------------------------------------
    // 404 Not Found
    // -------------------------------------------------------------------------

    @ExceptionHandler(ItineraryNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ItineraryNotFoundException ex,
            HttpServletRequest request) {

        log.warn("Itinerary not found – path={}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    // -------------------------------------------------------------------------
    // 401 Unauthorized – authentication required
    // -------------------------------------------------------------------------

    @ExceptionHandler(AuthenticationRequiredException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationRequired(
            AuthenticationRequiredException ex,
            HttpServletRequest request) {

        log.warn("Authentication required – path={}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    // -------------------------------------------------------------------------
    // 403 Forbidden
    // -------------------------------------------------------------------------

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(
            InvalidTokenException ex,
            HttpServletRequest request) {

        log.warn("Invalid access token – path={}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    // -------------------------------------------------------------------------
    // 429 Too Many Requests - Queue Full
    // -------------------------------------------------------------------------

    @ExceptionHandler(QueueFullException.class)
    public ResponseEntity<ErrorResponse> handleQueueFull(
            QueueFullException ex,
            HttpServletRequest request) {

        log.warn("Processing queue full – path={}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request);
    }

    // -------------------------------------------------------------------------
    // 429 Too Many Requests - Rate Limit Exceeded
    // -------------------------------------------------------------------------

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(
            RateLimitExceededException ex,
            HttpServletRequest request) {

        log.warn("Rate limit exceeded – path={} userType={} limit={} count={}: {}",
                request.getRequestURI(),
                ex.getUserType(),
                ex.getLimit(),
                ex.getCurrentCount(),
                ex.getMessage());

        // Build standard error response
        ErrorResponse body = new ErrorResponse(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                Instant.now()
        );

        // Add rate limit information as HTTP headers (standard practice)
        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", String.valueOf(ex.getRetryAfter()));
        headers.add("X-RateLimit-Limit", String.valueOf(ex.getLimit()));
        headers.add("X-RateLimit-Remaining", "0");
        headers.add("X-RateLimit-Reset", String.valueOf(System.currentTimeMillis() / 1000 + ex.getRetryAfter()));
        headers.add("X-RateLimit-UserType", ex.getUserType());

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .headers(headers)
                .body(body);
    }

    // -------------------------------------------------------------------------
    // 502 Bad Gateway
    // -------------------------------------------------------------------------

    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ErrorResponse> handleExternalService(
            ExternalServiceException ex,
            HttpServletRequest request) {

        log.error("External service failure [{}] – path={}: {}",
                  ex.getServiceName(), request.getRequestURI(), ex.getMessage(), ex);
        String message = String.format("Upstream service '%s' is unavailable: %s",
                                       ex.getServiceName(), ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, message, request);
    }

    // -------------------------------------------------------------------------
    // 400 Bad Request – Bean Validation
    // -------------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        String details = ex.getBindingResult()
                           .getFieldErrors()
                           .stream()
                           .map(FieldError::getDefaultMessage)
                           .collect(Collectors.joining("; "));

        log.warn("Validation failure – path={}: {}", request.getRequestURI(), details);
        String message = details.isBlank() ? "Validation failed" : details;
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    // -------------------------------------------------------------------------
    // 400 Bad Request – Missing required request parameter
    // -------------------------------------------------------------------------

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex,
            HttpServletRequest request) {

        log.warn("Missing required parameter – path={}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST,
                     "Required parameter '" + ex.getParameterName() + "' is missing", request);
    }

    // -------------------------------------------------------------------------
    // 400 Bad Request – Wrong parameter type
    // -------------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {

        log.warn("Parameter type mismatch – path={}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST,
                     "Invalid value for parameter '" + ex.getName() + "'", request);
    }

    // -------------------------------------------------------------------------
    // 500 Internal Server Error – catch-all
    // -------------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unhandled exception – path={}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                     "An unexpected error occurred. Please try again later.",
                     request);
    }

    // -------------------------------------------------------------------------
    // Builder helper
    // -------------------------------------------------------------------------

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status,
            String message,
            HttpServletRequest request) {

        ErrorResponse body = new ErrorResponse(
            status.value(),
            status.getReasonPhrase(),
            message,
            request.getRequestURI(),
            Instant.now()
        );
        return ResponseEntity.status(status).body(body);
    }
}
