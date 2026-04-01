package com.travel.itinerary.api.kafka;

import java.util.UUID;

/**
 * Event published by a worker service upon failure of a processing stage.
 * The Saga Orchestrator uses this to decide whether to retry or compensate.
 *
 * @param itineraryId  UUID of the itinerary that failed
 * @param workerType   failed stage: GEO, ROUTE, AI, POI
 * @param errorMessage human-readable failure reason
 * @param sagaVersion  version of the saga being processed (for idempotency)
 */
public record WorkerFailedEvent(
        UUID   itineraryId,
        String workerType,
        String errorMessage,
        Long   sagaVersion
) {
    /**
     * Constructor for backward compatibility (defaults to version 1).
     */
    public WorkerFailedEvent(UUID itineraryId, String workerType, String errorMessage) {
        this(itineraryId, workerType, errorMessage, 1L);
    }
}
