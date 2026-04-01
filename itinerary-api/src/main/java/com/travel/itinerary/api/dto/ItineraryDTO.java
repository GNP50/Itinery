package com.travel.itinerary.api.dto;

import com.travel.itinerary.api.dto.StepDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Container interface grouping all Itinerary-related request/response DTOs.
 * <p>
 * Every nested type is a Java 21 record so that instances are immutable,
 * automatically provide {@code equals}, {@code hashCode} and {@code toString},
 * and serialise cleanly with Jackson.
 */
public interface ItineraryDTO {

    /**
     * Payload accepted when creating or replacing an itinerary.
     *
     * @param title          human-readable title
     * @param description    optional free-text description
     * @param travelMode     e.g. {@code "DRIVING"}, {@code "CYCLING"}, {@code "WALKING"}
     * @param steps          ordered list of step requests
     * @param preferences    arbitrary key/value preferences forwarded to the AI engine
     */
    record Request(
            @NotBlank(message = "Title is required") String title,
            String description,
            @NotBlank(message = "Travel mode is required") String travelMode,
            @NotEmpty(message = "At least one step is required") List<StepDTO.Request> steps,
            Map<String, Object> preferences
    ) {}

    /**
     * Full representation of an itinerary returned to the client.
     *
     * @param id                     itinerary UUID
     * @param accessToken            bearer token that authorises write operations
     * @param title                  human-readable title
     * @param description            optional free-text description
     * @param status                 lifecycle status (e.g. {@code "PENDING"}, {@code "PROCESSING"}, {@code "COMPLETED"})
     * @param queuePosition          position in the processing queue ({@code null} when not queued)
     * @param estimatedCompletion    ISO-8601 timestamp of the expected completion ({@code null} when unknown)
     * @param travelMode             mode of transport
     * @param totalDistanceKm        sum of all inter-step distances
     * @param totalDurationMinutes   sum of all inter-step travel times
     * @param currentStepIndex       zero-based index of the step the traveller is currently at
     * @param steps                  ordered list of enriched step responses
     * @param preferences            itinerary-level preferences (interests, avoidHighways, generateAiTips, etc.)
     * @param aiSuggestions          arbitrary AI-generated metadata for the whole itinerary
     * @param createdAt              ISO-8601 creation timestamp
     * @param updatedAt              ISO-8601 last-update timestamp
     */
    record Response(
            UUID id,
            String accessToken,
            String title,
            String description,
            String status,
            Integer queuePosition,
            String estimatedCompletion,
            String travelMode,
            BigDecimal totalDistanceKm,
            Integer totalDurationMinutes,
            Integer currentStepIndex,
            List<StepDTO.Response> steps,
            Map<String, Object> preferences,
            Map<String, Object> aiSuggestions,
            String createdAt,
            String updatedAt
    ) {}

    /**
     * Lightweight status snapshot used for polling.
     *
     * @param id                  itinerary UUID
     * @param status              current lifecycle status
     * @param queuePosition       position in the processing queue
     * @param estimatedCompletion ISO-8601 estimated completion timestamp
     * @param progressPercent     overall completion percentage (0–100)
     */
    record StatusResponse(
            UUID id,
            String status,
            Integer queuePosition,
            String estimatedCompletion,
            int progressPercent
    ) {}

    /**
     * Minimal acknowledgement returned immediately after a creation request
     * has been accepted and enqueued.
     *
     * @param id                  itinerary UUID
     * @param accessToken         bearer token for subsequent operations
     * @param status              initial status (typically {@code "PENDING"})
     * @param queuePosition       initial queue position
     * @param estimatedCompletion ISO-8601 estimated completion timestamp
     */
    record CreateResponse(
            UUID id,
            String accessToken,
            String status,
            Integer queuePosition,
            String estimatedCompletion
    ) {}

    /**
     * Lightweight summary of an itinerary for list views.
     *
     * @param id          itinerary UUID
     * @param title       human-readable title
     * @param status      current lifecycle status
     * @param travelMode  mode of transport
     * @param stepCount   number of steps in the itinerary
     * @param createdAt   ISO-8601 creation timestamp
     */
    record ItinerarySummary(
            UUID id,
            String title,
            String status,
            String travelMode,
            int stepCount,
            String createdAt
    ) {}

    /**
     * Response containing a list of itinerary summaries for the current user.
     *
     * @param itineraries list of itinerary summaries
     */
    record ListResponse(
            List<ItinerarySummary> itineraries
    ) {}
}
