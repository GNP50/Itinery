package com.travel.itinerary.api.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published each time a single itinerary step has been fully
 * processed (geocoded, routed, and AI-enriched).
 *
 * @param itineraryId        UUID of the parent itinerary
 * @param stepId             UUID of the processed step
 * @param stepOrder          1-based position of the step within the itinerary
 * @param placeName          canonical place name resolved from geocoding
 * @param latitude           WGS-84 latitude of the step location
 * @param longitude          WGS-84 longitude of the step location
 * @param distanceFromPrevKm road distance from the previous step in kilometres
 *                           ({@code null} for the first step)
 * @param correlationId      trace/correlation identifier for distributed tracing
 * @param occurredAt         instant at which the event was emitted
 */
public record StepProcessedEvent(
        UUID itineraryId,
        UUID stepId,
        int stepOrder,
        String placeName,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal distanceFromPrevKm,
        String correlationId,
        Instant occurredAt
) {}
