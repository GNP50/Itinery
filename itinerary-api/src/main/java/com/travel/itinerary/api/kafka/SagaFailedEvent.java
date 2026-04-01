package com.travel.itinerary.api.kafka;

import java.util.UUID;

/**
 * Kafka event published by the saga orchestrator when a saga fails after max retries.
 * This event is consumed by the itinerary domain to mark the itinerary as FAILED
 * using proper port/adapter pattern.
 *
 * @param itineraryId UUID of the failed itinerary
 * @param sagaId      UUID of the failed saga instance
 * @param failedStep  The step that failed (e.g., "GEO", "ROUTE", "AI", "POI")
 * @param reason      Error message describing the failure
 */
public record SagaFailedEvent(
        UUID itineraryId,
        UUID sagaId,
        String failedStep,
        String reason
) {}
