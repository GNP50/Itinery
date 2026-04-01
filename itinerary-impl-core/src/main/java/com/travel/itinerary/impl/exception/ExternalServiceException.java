package com.travel.itinerary.impl.exception;

/**
 * Thrown when a call to an external service (geocoding, routing, AI engine,
 * MinIO, etc.) fails with an unrecoverable error.
 * <p>
 * The global exception handler maps this to an HTTP 502 Bad Gateway response,
 * indicating that the server received an invalid response from an upstream
 * service.
 */
public class ExternalServiceException extends RuntimeException {

    /** Identifies the external service that failed (e.g. {@code "OSRM"}, {@code "OpenAI"}). */
    private final String serviceName;

    /**
     * Constructs the exception with the name of the failing service and a detail message.
     *
     * @param serviceName identifier of the external service; must not be {@code null}
     * @param message     human-readable description of the failure
     */
    public ExternalServiceException(String serviceName, String message) {
        super(message);
        this.serviceName = serviceName;
    }

    /**
     * Constructs the exception with the name of the failing service, a detail message,
     * and the underlying cause.
     *
     * @param serviceName identifier of the external service; must not be {@code null}
     * @param message     human-readable description of the failure
     * @param cause       underlying cause
     */
    public ExternalServiceException(String serviceName, String message, Throwable cause) {
        super(message, cause);
        this.serviceName = serviceName;
    }

    /**
     * Returns the name of the external service that raised this exception.
     *
     * @return non-null service name string
     */
    public String getServiceName() {
        return serviceName;
    }
}
