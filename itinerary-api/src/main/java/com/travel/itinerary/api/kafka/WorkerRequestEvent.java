package com.travel.itinerary.api.kafka;

import java.util.UUID;

/**
 * Event published by the Saga Orchestrator to a worker topic requesting
 * a specific processing stage (GEO, ROUTE, AI, POI) for an itinerary.
 *
 * @param itineraryId     UUID of the itinerary to process
 * @param workerType      processing stage: GEO, ROUTE, AI, POI
 * @param preferencesJson JSON blob with user preferences (interests, avoidHighways, generateAiTips, etc.)
 * @param sagaVersion     version of the saga (included in worker response for idempotency)
 * @param userType        user type (ADMIN, REGISTERED, ANONYMOUS) for priority-based routing
 */
public record WorkerRequestEvent(
        UUID   itineraryId,
        String workerType,
        String preferencesJson,
        Long   sagaVersion,
        String userType
) {
    /**
     * Constructor for backward compatibility (defaults to version 1 and ANONYMOUS user type).
     */
    public WorkerRequestEvent(UUID itineraryId, String workerType, String preferencesJson) {
        this(itineraryId, workerType, preferencesJson, 1L, "ANONYMOUS");
    }

    /**
     * Constructor for backward compatibility (defaults to ANONYMOUS user type).
     */
    public WorkerRequestEvent(UUID itineraryId, String workerType, String preferencesJson, Long sagaVersion) {
        this(itineraryId, workerType, preferencesJson, sagaVersion, "ANONYMOUS");
    }
}
