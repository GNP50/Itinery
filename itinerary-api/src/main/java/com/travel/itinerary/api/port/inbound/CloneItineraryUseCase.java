package com.travel.itinerary.api.port.inbound;

import com.travel.itinerary.api.dto.ItineraryDTO;

import java.util.UUID;

/**
 * Inbound port: clone an existing itinerary and start it.
 * <p>
 * This use case creates a copy of an existing itinerary with all its steps,
 * persists it with QUEUED status, enqueues it for async enrichment,
 * and returns an acknowledgement with the assigned identifiers.
 */
public interface CloneItineraryUseCase {

    /**
     * Clone an existing itinerary and enqueue it for processing.
     *
     * @param id    the UUID of the itinerary to clone; must not be {@code null}
     * @param token access token for the source itinerary; may be {@code null} if authenticated
     * @return a {@link ItineraryDTO.CreateResponse} containing the assigned UUID,
     *         access token, initial status and queue position of the cloned itinerary
     */
    ItineraryDTO.CreateResponse cloneItinerary(UUID id, String token);
}

