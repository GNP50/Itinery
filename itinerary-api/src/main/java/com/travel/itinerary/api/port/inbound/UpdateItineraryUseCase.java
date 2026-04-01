package com.travel.itinerary.api.port.inbound;

import com.travel.itinerary.api.dto.ItineraryDTO;

import java.util.UUID;

/**
 * Inbound port: replace the mutable fields of an existing itinerary.
 * <p>
 * An update triggers re-enrichment when the set of steps or the travel mode
 * has changed; the itinerary is reset to {@code PENDING} status and
 * re-queued for processing.
 */
public interface UpdateItineraryUseCase {

    /**
     * Apply a full replacement update to an existing itinerary.
     *
     * @param id      itinerary UUID; must not be {@code null}
     * @param token   access token issued at creation time; must not be {@code null}
     * @param request replacement payload; must not be {@code null}
     * @return the updated full {@link ItineraryDTO.Response}
     */
    ItineraryDTO.Response update(UUID id, String token, ItineraryDTO.Request request);
}
