package com.travel.itinerary.api.kafka;

import java.util.UUID;

/**
 * Kafka event published by the saga orchestrator when a saga completes successfully.
 * This event is consumed by the itinerary domain to mark the itinerary as COMPLETED
 * using proper port/adapter pattern.
 *
 * @param itineraryId UUID of the completed itinerary
 * @param sagaId      UUID of the completed saga instance
 */
public record SagaCompletedEvent(
        UUID itineraryId,
        UUID sagaId
) {}
