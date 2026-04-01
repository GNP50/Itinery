package com.travel.itinerary.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Data Transfer Objects for the admin dashboard.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminDTO {

    private AdminDTO() {}

    /**
     * Summary row for the admin itinerary list.
     */
    public record ItinerarySummary(
        UUID    id,
        String  title,
        String  status,
        UUID    ownerId,
        String  ownerEmail,
        String  ownerName,
        String  ownerType,
        Instant createdAt,
        Integer queuePosition,
        String  accessToken
    ) {}

    /**
     * Paginated response for the admin itinerary list.
     */
    public record PagedItineraries(
        List<ItinerarySummary> items,
        long   totalElements,
        int    totalPages,
        int    page,
        int    size
    ) {}
}
