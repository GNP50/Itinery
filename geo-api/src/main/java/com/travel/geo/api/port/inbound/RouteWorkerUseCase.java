package com.travel.geo.api.port.inbound;

import java.util.UUID;

/**
 * Use-case port for routing worker operations.
 * <p>
 * Calculates routes between consecutive steps of an itinerary,
 * updating distance, duration, and route geometry fields.
 */
public interface RouteWorkerUseCase {

    /**
     * Routes between consecutive steps of the given itinerary and persists the results.
     *
     * @param itineraryId UUID of the itinerary to process
     * @throws IllegalStateException if the itinerary is not found
     */
    void processRouting(UUID itineraryId);
}
