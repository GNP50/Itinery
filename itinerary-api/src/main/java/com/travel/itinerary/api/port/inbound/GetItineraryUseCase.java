package com.travel.itinerary.api.port.inbound;

import com.travel.itinerary.api.dto.ItineraryDTO;

import java.util.UUID;

/**
 * Inbound port: read operations for a single itinerary.
 * <p>
 * Both methods require the caller to supply the access token that was issued
 * at creation time so that itineraries are effectively private to the creator.
 */
public interface GetItineraryUseCase {

    /**
     * Retrieve the full representation of an itinerary, including all
     * enriched steps, AI suggestions and route geometry.
     *
     * @param id    itinerary UUID; must not be {@code null}
     * @param token access token issued at creation time; must not be {@code null}
     * @return full {@link ItineraryDTO.Response}
     */
    ItineraryDTO.Response getById(UUID id, String token);

    /**
     * Retrieve a lightweight status snapshot suitable for polling during
     * asynchronous processing.
     *
     * @param id    itinerary UUID; must not be {@code null}
     * @param token access token issued at creation time; must not be {@code null}
     * @return {@link ItineraryDTO.StatusResponse} with progress information
     */
    ItineraryDTO.StatusResponse getStatus(UUID id, String token);
}
