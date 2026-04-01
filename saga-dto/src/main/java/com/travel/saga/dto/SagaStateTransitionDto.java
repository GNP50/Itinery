package com.travel.saga.dto;

import lombok.Builder;

import java.time.Instant;

/**
 * Data Transfer Object for Saga State Transition history.
 */
@Builder
public record SagaStateTransitionDto(
    String fromState,
    String toState,
    String event,
    Instant timestamp
) {
}
