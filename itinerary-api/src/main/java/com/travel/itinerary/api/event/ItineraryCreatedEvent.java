package com.travel.itinerary.api.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Domain event published when a new itinerary has been accepted and
 * enqueued for processing.
 *
 * @param id              UUID of the newly created itinerary
 * @param accessToken     bearer token assigned to the itinerary owner
 * @param userType        user type (REGISTERED, ADMIN, ANONYMOUS) for priority processing
 * @param title           human-readable itinerary title
 * @param travelMode      mode of transport (e.g. {@code "DRIVING"})
 * @param stepCount       number of steps requested
 * @param stepPlaceNames  ordered list of place names from the request steps
 * @param preferencesJson JSON blob with user preferences (interests, avoidHighways, generateAiTips, etc.)
 * @param correlationId   trace/correlation identifier for distributed tracing
 * @param occurredAt      instant at which the event was emitted
 */
public record ItineraryCreatedEvent(
        UUID         id,
        String       accessToken,
        String       userType,
        String       title,
        String       travelMode,
        int          stepCount,
        List<String> stepPlaceNames,
        String       preferencesJson,
        String       correlationId,
        Instant      occurredAt
) {}
