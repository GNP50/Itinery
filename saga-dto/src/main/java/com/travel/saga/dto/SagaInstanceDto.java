package com.travel.saga.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Data Transfer Object for Saga Instance.
 */
@Builder
public record SagaInstanceDto(
    UUID id,
    UUID itineraryId,
    String currentState,
    Set<String> completedSteps,
    String failedStep,
    String errorMessage,
    long version,
    int retryCount,
    int maxRetries,
    String preferences,
    Instant createdAt,
    Instant updatedAt
) {
}
