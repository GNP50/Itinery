package com.travel.saga.dto;

import lombok.Builder;

import java.util.UUID;

/**
 * Request DTO for listing saga instances with filters.
 */
@Builder
public record SagaListRequest(
    int offset,
    int limit,
    String stateFilter,
    UUID itineraryId
) {
    public static SagaListRequest of(int offset, int limit) {
        return new SagaListRequest(offset, limit, null, null);
    }
}
