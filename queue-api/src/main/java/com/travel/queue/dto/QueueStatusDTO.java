package com.travel.queue.dto;

import java.util.List;
import java.util.UUID;

/**
 * Container interface grouping queue-status DTOs.
 * Generic queue status DTO used across all domains (itinerary, geo, ai, saga).
 */
public interface QueueStatusDTO {

    /**
     * Full snapshot of the processing queue with separate metrics
     * for registered and anonymous user queues.
     *
     * @param totalQueueLength      total number of items across both queues
     * @param registeredQueueSize   number of items in the registered/admin user queue (high priority)
     * @param anonymousQueueSize    number of items in the anonymous user queue (low priority)
     * @param items                 ordered list of queue entries (optional, may be empty)
     * @param maxQueueSize          maximum number of items that can be queued (from queue.max-size config)
     * @param maxConcurrent         maximum number of parallel processing executions (from saga.max-concurrent config)
     * @param processingCount       current number of items being processed
     */
    record Response(
            int totalQueueLength,
            int registeredQueueSize,
            int anonymousQueueSize,
            List<QueueItem> items,
            int maxQueueSize,
            int maxConcurrent,
            int processingCount
    ) {}

    /**
     * Single entry in the processing queue.
     *
     * @param id                  item UUID (generic - could be itinerary, geo request, etc.)
     * @param title               item title/description
     * @param status              current lifecycle status
     * @param queuePosition       RELATIVE 1-based position (1 = next to be processed)
     *                            This value is calculated dynamically from Redis and is always accurate
     * @param estimatedCompletion ISO-8601 estimated completion timestamp
     */
    record QueueItem(
            UUID id,
            String title,
            String status,
            Integer queuePosition,
            String estimatedCompletion
    ) {}
}
