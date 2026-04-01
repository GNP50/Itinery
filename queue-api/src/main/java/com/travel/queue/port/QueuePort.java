package com.travel.queue.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound SPI: durable FIFO queue for asynchronous processing.
 * <p>
 * Implementations may use an in-process queue, Redis, Kafka, RabbitMQ, or any
 * compatible message broker / queue backend.
 * <p>
 * This is a cross-cutting infrastructure concern used by multiple bounded contexts.
 *
 * @param <T> the queued item type (typically a domain entity UUID or a lightweight processing command)
 */
public interface QueuePort<T> {

    /**
     * Add an item to the tail of the queue.
     *
     * @param item the item to enqueue; must not be {@code null}
     * @return the 1-based position of the item in the queue immediately after enqueuing
     */
    int enqueue(T item);

    /**
     * Remove and return the item at the head of the queue (non-blocking).
     *
     * @return an {@link Optional} containing the next item, or empty if the queue is empty
     */
    Optional<T> dequeueNext();

    /**
     * Query the current position of an item in the queue.
     *
     * @param itemId UUID of the item to locate; must not be {@code null}
     * @return 1-based queue position, or {@code -1} if the item is not present
     */
    int getPosition(UUID itemId);

    /**
     * Remove the item associated with the given UUID from any position in the queue
     * (e.g. after cancellation).
     *
     * @param itemId UUID of the item to remove; must not be {@code null}
     * @return {@code true} if the item was found and removed, {@code false} if it was not in the queue
     */
    boolean remove(UUID itemId);

    /**
     * Return the current number of items in the queue.
     *
     * @return queue size; always {@code >= 0}
     */
    int size();
}
