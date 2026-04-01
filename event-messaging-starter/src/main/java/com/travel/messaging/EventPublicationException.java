package com.travel.messaging;

/**
 * Unchecked exception thrown by {@link EventPublisherPort} implementations
 * when a domain event cannot be persisted to the outbox table.
 *
 * <p>Common causes include JSON serialisation failures and database constraint
 * violations.  Because this exception is unchecked it will automatically roll
 * back any surrounding {@code @Transactional} boundary, which is the desired
 * behaviour – if we cannot record the event we must not commit the business
 * state change either.
 */
public class EventPublicationException extends RuntimeException {

    public EventPublicationException(String message) {
        super(message);
    }

    public EventPublicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
