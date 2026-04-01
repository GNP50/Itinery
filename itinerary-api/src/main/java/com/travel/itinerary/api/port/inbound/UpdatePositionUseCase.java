package com.travel.itinerary.api.port.inbound;

import com.travel.itinerary.api.dto.PositionUpdateDTO;

import java.util.UUID;

/**
 * Inbound port: record the traveller's current geographic position and
 * advance the active step index.
 */
public interface UpdatePositionUseCase {

    /**
     * Update the current position of the traveller following this itinerary.
     *
     * @param id      itinerary UUID; must not be {@code null}
     * @param token   access token issued at creation time; must not be {@code null}
     * @param request position payload containing the new step index and coordinates;
     *                must not be {@code null}
     * @return a {@link PositionUpdateDTO.Response} confirming the persisted state
     */
    PositionUpdateDTO.Response updatePosition(UUID id, String token, PositionUpdateDTO.Request request);
}
