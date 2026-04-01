package com.travel.itinerary.impl.exception;

import java.util.UUID;

/**
 * Thrown when an itinerary cannot be found by the requested identifier.
 * <p>
 * The global exception handler maps this to an HTTP 404 Not Found response.
 */
public class ItineraryNotFoundException extends RuntimeException {

    /**
     * Constructs the exception with a custom detail message.
     *
     * @param message human-readable description of the missing resource
     */
    public ItineraryNotFoundException(String message) {
        super(message);
    }

    /**
     * Convenience constructor that formats a standard message from the UUID.
     *
     * @param id the UUID that was not found
     */
    public ItineraryNotFoundException(UUID id) {
        super("Itinerary not found: " + id);
    }
}
