package com.travel.itinerary.api.port.inbound;

import java.util.UUID;

/**
 * Inbound port: update itinerary status based on saga lifecycle events.
 * <p>
 * This use case is consumed by Kafka listeners that react to saga completion
 * or failure events, maintaining proper hexagonal architecture boundaries.
 */
public interface MarkItineraryStatusUseCase {

    /**
     * Mark an itinerary as COMPLETED after saga successfully completes all steps.
     *
     * @param itineraryId UUID of the itinerary to mark as completed
     */
    void markCompleted(UUID itineraryId) throws Throwable;

    /**
     * Mark an itinerary as FAILED after saga fails after max retries.
     *
     * @param itineraryId UUID of the itinerary to mark as failed
     * @param failedStep  The step that failed (e.g., "GEO", "ROUTE", "AI", "POI")
     * @param reason      Error message describing the failure
     */
    void markFailed(UUID itineraryId, String failedStep, String reason) throws Throwable;
}
