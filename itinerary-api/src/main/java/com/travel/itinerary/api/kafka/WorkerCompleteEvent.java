package com.travel.itinerary.api.kafka;

import java.util.UUID;

/**
 * Event published by a worker service upon successful completion of a
 * processing stage, consumed by the Saga Orchestrator to advance state.
 *
 * @param itineraryId UUID of the processed itinerary
 * @param workerType  completed stage: GEO, ROUTE, AI, POI
 * @param sagaVersion version of the saga being processed (for idempotency)
 */
public record WorkerCompleteEvent(
        UUID   itineraryId,
        String workerType,
        Long   sagaVersion
) {
    /**
     * Constructor for backward compatibility (defaults to version 1).
     */
    public WorkerCompleteEvent(UUID itineraryId, String workerType) {
        this(itineraryId, workerType, 1L);
    }
}
