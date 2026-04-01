package com.travel.queue.impl.redis;

import com.travel.queue.dto.QueueStatusDTO;
import com.travel.queue.port.QueueManagementPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Redis-based implementation of {@link QueueManagementPort} using UnifiedQueueManager.
 * <p>
 * This adapter delegates to the new {@link UnifiedQueueManager} which uses a Redis
 * Sorted Set for accurate FIFO-with-priority queue management.
 * <p>
 * <b>Migration Note:</b> This class now uses UnifiedQueueManager instead of the legacy
 * RedisQueueAdapter with separate queues.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisQueueManagementAdapter implements QueueManagementPort {

    private final UnifiedQueueManager unifiedQueueManager;

    @Value("${queue.max-size:1000}")
    private int maxQueueSize;

    @Value("${saga.max-concurrent:5}")
    private int maxConcurrent;

    @Override
    public int enqueue(UUID itineraryId, String userType) {
        // Delegate to unified queue manager with priority based on user type
        // TODO: Pass actual userId when available from context
        return unifiedQueueManager.enqueue(itineraryId, userType, null);
    }

    @Override
    public void remove(UUID itineraryId) {
        unifiedQueueManager.remove(itineraryId);
    }

    @Override
    public void cancelProcessing(UUID itineraryId) {
        // For now, just log - actual cancellation would require tracking active processing tasks
        log.info("Cancellation requested for itinerary id={}", itineraryId);
        // In a full implementation, this would interact with a task executor to interrupt the task
    }

    @Override
    public QueueStatusDTO.Response getQueueStatus() {
        UnifiedQueueManager.QueueStatistics stats = unifiedQueueManager.getStatistics();

        return new QueueStatusDTO.Response(
            (int) stats.totalQueued(),
            (int) stats.registeredCount(),
            (int) stats.anonymousCount(),
            List.of(), // Currently processing items - tracking available via stats.totalProcessing()
            maxQueueSize,
            maxConcurrent,
            (int) stats.totalProcessing()
        );
    }

    @Override
    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    @Override
    public boolean markProcessingStarted(UUID itineraryId) {
        return unifiedQueueManager.markProcessingStarted(itineraryId);
    }

    @Override
    public int getPositionInQueue(UUID itineraryId) {
        return unifiedQueueManager.getPosition(itineraryId);
    }

    @Override
    public void markProcessingCompleted(UUID itineraryId) {
        unifiedQueueManager.markProcessingCompleted(itineraryId);
    }

    @Override
    public List<UUID> getAllQueuedItems() {
        return unifiedQueueManager.getAllQueuedItems();
    }
}
