package com.travel.itinerary.impl.exception;

/**
 * Thrown when a new itinerary cannot be enqueued because the processing queue
 * has reached its maximum capacity.
 * <p>
 * The global exception handler maps this to an HTTP 429 Too Many Requests
 * response, signalling to callers that they should retry after a delay.
 */
public class QueueFullException extends RuntimeException {

    /**
     * Constructs the exception with a detail message.
     *
     * @param message human-readable explanation (e.g. current capacity)
     */
    public QueueFullException(String message) {
        super(message);
    }

    /**
     * Constructs the exception with a detail message and a causal exception.
     *
     * @param message human-readable explanation
     * @param cause   underlying cause
     */
    public QueueFullException(String message, Throwable cause) {
        super(message, cause);
    }
}
