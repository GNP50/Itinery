package com.travel.itinerary.api.port.inbound;

import java.util.UUID;

/**
 * Inbound port: permanently remove an itinerary and all its associated data.
 */
public interface DeleteItineraryUseCase {

    /**
     * Delete an itinerary and all of its steps, enrichment data and cached
     * artefacts.
     *
     * @param id    itinerary UUID; must not be {@code null}
     * @param token access token issued at creation time; must not be {@code null}
     */
    void delete(UUID id, String token);
}
