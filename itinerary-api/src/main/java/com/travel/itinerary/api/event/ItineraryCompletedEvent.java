package com.travel.itinerary.api.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published when all steps of an itinerary have been
 * successfully geocoded, routed, and AI-enriched.
 *
 * @param id                   UUID of the completed itinerary
 * @param title                human-readable itinerary title
 * @param totalDistanceKm      cumulative road distance across all steps in kilometres
 * @param totalDurationMinutes cumulative estimated travel time across all steps in minutes
 * @param stepCount            number of steps in the completed itinerary
 * @param correlationId        trace/correlation identifier for distributed tracing
 * @param occurredAt           instant at which the event was emitted
 */
public record ItineraryCompletedEvent(
        UUID id,
        String title,
        BigDecimal totalDistanceKm,
        Integer totalDurationMinutes,
        int stepCount,
        String correlationId,
        Instant occurredAt
) {}
