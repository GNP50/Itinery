package com.travel.user.impl.exception;

/**
 * Thrown when an authentication or authorisation operation fails
 * (e.g. bad credentials, email already registered).
 */
public class AuthException extends RuntimeException {
    public AuthException(String message) {
        super(message);
    }
}
