package com.travel.saga.dto;

import lombok.Builder;

/**
 * Result DTO for saga command operations (retry, compensate).
 */
@Builder
public record SagaOperationResult(
    boolean success,
    String message
) {
    public static SagaOperationResult success(String message) {
        return new SagaOperationResult(true, message);
    }

    public static SagaOperationResult failure(String message) {
        return new SagaOperationResult(false, message);
    }
}
