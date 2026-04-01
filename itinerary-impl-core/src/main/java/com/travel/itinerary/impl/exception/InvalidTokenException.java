package com.travel.itinerary.impl.exception;

/**
 * Thrown when the supplied access token does not match the token that was
 * issued to the owner of the requested itinerary.
 * <p>
 * The global exception handler maps this to an HTTP 403 Forbidden response.
 * A 403 (rather than 401) is used deliberately: the request is authenticated
 * but not authorised for this specific resource.
 */
public class InvalidTokenException extends RuntimeException {

    /**
     * Constructs the exception with a detail message.
     *
     * @param message human-readable explanation
     */
    public InvalidTokenException(String message) {
        super(message);
    }

    /**
     * Constructs the exception with a detail message and a causal exception.
     *
     * @param message human-readable explanation
     * @param cause   underlying cause
     */
    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
