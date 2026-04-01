package com.travel.geo.api.port.inbound;

import java.util.UUID;

/**
 * Use-case port for POI discovery worker operations.
 * <p>
 * Discovers points of interest near each step of an itinerary
 * and stores them as JSON.
 */
public interface PoiWorkerUseCase {

    /**
     * Discovers POIs for all steps of the given itinerary and persists the results.
     * Also marks the itinerary as COMPLETED and publishes the completion event.
     *
     * @param itineraryId UUID of the itinerary to process
     * @throws IllegalStateException if the itinerary is not found
     */
    void processPoiDiscovery(UUID itineraryId);
}
