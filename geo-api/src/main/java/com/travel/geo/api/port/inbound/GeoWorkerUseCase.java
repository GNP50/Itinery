package com.travel.geo.api.port.inbound;

import java.util.UUID;

/**
 * Use-case port for geocoding worker operations.
 * <p>
 * Geocodes all steps of an itinerary, updating their lat/lon coordinates
 * and resolved address fields (city, region, country).
 */
public interface GeoWorkerUseCase {

    /**
     * Geocodes all steps of the given itinerary and persists the results.
     *
     * @param itineraryId UUID of the itinerary to process
     * @throws IllegalStateException if the itinerary is not found
     */
    void processGeocoding(UUID itineraryId);
}
