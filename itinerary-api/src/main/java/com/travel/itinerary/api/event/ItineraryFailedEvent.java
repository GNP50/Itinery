package com.travel.itinerary.api.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published when itinerary processing terminates with an
 * unrecoverable error.
 *
 * @param id            UUID of the failed itinerary
 * @param title         human-readable itinerary title
 * @param reason        human-readable description of the failure cause
 * @param correlationId trace/correlation identifier for distributed tracing
 * @param occurredAt    instant at which the event was emitted
 */
public record ItineraryFailedEvent(
        UUID id,
        String title,
        String reason,
        String correlationId,
        Instant occurredAt
) {}
