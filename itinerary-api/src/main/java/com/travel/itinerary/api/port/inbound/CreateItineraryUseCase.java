package com.travel.itinerary.api.port.inbound;

import com.travel.itinerary.api.dto.ItineraryDTO;

/**
 * Inbound port: primary use-case for creating a new itinerary.
 * <p>
 * The implementing application service validates the request, persists a
 * skeleton itinerary, enqueues it for async enrichment, and immediately
 * returns an acknowledgement with the assigned identifiers.
 */
public interface CreateItineraryUseCase {

    /**
     * Accept a new itinerary creation request.
     *
     * @param request the creation payload; must not be {@code null}
     * @return a {@link ItineraryDTO.CreateResponse} containing the assigned UUID,
     *         access token, initial status and queue position
     */
    ItineraryDTO.CreateResponse create(ItineraryDTO.Request request);
}
