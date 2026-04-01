package com.travel.itinerary.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Container interface grouping DTOs used when a traveller reports their
 * current position / step progress.
 */
public interface PositionUpdateDTO {

    /**
     * Payload sent by the client to update the traveller's position.
     *
     * @param currentStepIndex zero-based index of the step the traveller has reached
     * @param latitude         WGS-84 latitude reported by the client device
     * @param longitude        WGS-84 longitude reported by the client device
     */
    record Request(
            Integer currentStepIndex,
            BigDecimal latitude,
            BigDecimal longitude
    ) {}

    /**
     * Confirmation returned after the position has been recorded.
     *
     * @param id               itinerary UUID
     * @param currentStepIndex updated step index
     * @param updatedAt        ISO-8601 timestamp of the update
     */
    record Response(
            UUID id,
            Integer currentStepIndex,
            String updatedAt
    ) {}
}
