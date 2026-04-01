package com.travel.itinerary.api.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Domain event published when an existing itinerary has been updated and
 * re-queued for processing.
 * <p>
 * This triggers the saga orchestrator to restart the processing workflow
 * from the beginning for the updated itinerary.
 *
 * @param id              UUID of the updated itinerary
 * @param title           updated human-readable itinerary title
 * @param travelMode      updated mode of transport (e.g. {@code "DRIVING"})
 * @param stepCount       updated number of steps
 * @param stepPlaceNames  updated ordered list of place names from the request steps
 * @param preferencesJson updated JSON blob with user preferences
 * @param userType        user type for priority calculation (REGISTERED, ADMIN, ANONYMOUS)
 * @param correlationId   trace/correlation identifier for distributed tracing
 * @param occurredAt      instant at which the event was emitted
 */
public record ItineraryUpdatedEvent(
        UUID         id,
        String       title,
        String       travelMode,
        int          stepCount,
        List<String> stepPlaceNames,
        String       preferencesJson,
        String       userType,
        String       correlationId,
        Instant      occurredAt
) {}

