package com.travel.queue.port;

import com.travel.queue.dto.QueueStatusDTO;

import java.util.UUID;

/**
 * Outbound port for queue management operations.
 * <p>
 * Isolates the core domain from queue infrastructure (Redis, scheduling, concurrency).
 * Provides high-level queue operations like enqueuing with priority, status queries,
 * and processing cancellation.
 * <p>
 * This is a cross-cutting infrastructure concern used by multiple bounded contexts
 * (itinerary, geo, ai, saga orchestration).
 */
public interface QueueManagementPort {

    /**
     * Enqueues an item with the appropriate priority based on user type.
     * <p>
     * Registered users are added to a priority queue, anonymous users to a standard queue.
     * The actual queue implementation uses Redis Sorted Set for FIFO-with-priority.
     *
     * @param itemId UUID of the item to enqueue
     * @param userType user type ("REGISTERED", "ADMIN", "ANONYMOUS") determining priority
     * @return the queue position (1-based index) where the item was inserted
     */
    int enqueue(UUID itemId, String userType);

    /**
     * Removes an item from the queue.
     * <p>
     * This is a safe operation – if the item is not found in the queue,
     * the method returns silently without error.
     *
     * @param itemId UUID of the item to remove
     */
    void remove(UUID itemId);

    /**
     * Attempts to cancel active processing for an item.
     * <p>
     * If the item is currently being processed by a background worker,
     * this method interrupts the processing task and marks it for cancellation.
     *
     * @param itemId UUID of the item to cancel
     */
    void cancelProcessing(UUID itemId);

    /**
     * Returns the current queue status including queue lengths and processing capacity.
     *
     * @return snapshot of queue statistics
     */
    QueueStatusDTO.Response getQueueStatus();

    /**
     * Returns the maximum allowed queue size (capacity limit).
     *
     * @return maximum number of items that can be queued simultaneously
     */
    int getMaxQueueSize();

    /**
     * Marks an item as processing started and removes it from the queue.
     * <p>
     * This is the critical operation that ensures queue positions remain accurate.
     * When processing starts, the item is removed from the queue (ZREM), causing
     * all other items' positions to automatically update.
     * <p>
     * This method should be called by the saga orchestrator when it begins processing
     * (transitions from QUEUED to active processing state).
     *
     * @param itemId UUID of the item to mark as processing
     * @return {@code true} if the item was in the queue and removed, {@code false} otherwise
     */
    boolean markProcessingStarted(UUID itemId);

    /**
     * Gets the current queue position of an item.
     * <p>
     * This method queries the live position from Redis (the source of truth) and always
     * returns an accurate result. Positions are 1-based (1 = next to be processed).
     *
     * @param itemId UUID of the item to locate
     * @return 1-based position in the queue, or {@code -1} if not in queue
     */
    int getPositionInQueue(UUID itemId);

    /**
     * Marks an item as processing completed and cleans up tracking data.
     * <p>
     * This should be called when processing completes (successfully or with failure)
     * to clean up the processing tracker in Redis.
     *
     * @param itemId UUID of the item that completed processing
     */
    void markProcessingCompleted(UUID itemId);

    /**
     * Returns all items currently in the queue, ordered by priority and FIFO.
     * <p>
     * Items are returned in processing order: highest priority and oldest first.
     *
     * @return list of all queued item UUIDs
     */
    java.util.List<UUID> getAllQueuedItems();
}
