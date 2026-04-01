package com.travel.itinerary.impl.exception;

/**
 * Thrown when an operation requires authentication but no authenticated user is present.
 */
public class AuthenticationRequiredException extends RuntimeException {
    public AuthenticationRequiredException(String message) {
        super(message);
    }
}
