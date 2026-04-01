package com.travel.ai.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * DTOs for AI-powered travel suggestions for a specific itinerary step or place.
 * <p>
 * The request carries enough context (itinerary identity, place details, travel
 * mode and personal interests) for an AI model to produce highly personalised
 * recommendations without any additional data fetching on the service side.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface AiSuggestionDTO {

    /**
     * Inbound suggestion request.
     *
     * @param itineraryId  the UUID of the parent itinerary; must not be {@code null}
     * @param accessToken  caller's authentication token forwarded to the AI service;
     *                     must not be blank
     * @param placeName    canonical name of the place to enrich; must not be blank
     * @param city         city the place belongs to; may be {@code null}
     * @param region       region or state; may be {@code null}
     * @param country      full country name; may be {@code null}
     * @param travelMode   mode of transport used to arrive here (e.g. {@code "DRIVING"});
     *                     may be {@code null}
     * @param interests    traveller interest tags used to personalise suggestions
     *                     (e.g. {@code ["art", "food"]}); may be {@code null} or empty
     */
    record Request(
            @NotNull(message = "itineraryId must not be null")
            UUID itineraryId,

            @NotBlank(message = "accessToken must not be blank")
            String accessToken,

            @NotBlank(message = "placeName must not be blank")
            String placeName,

            String city,
            String region,
            String country,
            String travelMode,
            List<String> interests
    ) {
        public Request {
            interests = interests == null ? List.of() : List.copyOf(interests);
        }
    }

    /**
     * Outbound AI suggestion response.
     *
     * @param description         rich narrative description of the place
     * @param tips                practical traveller tips (e.g. best time to visit,
     *                            accessibility notes)
     * @param mustSee             curated list of highlights not to miss
     * @param localFood           local food or culinary recommendation
     * @param recommendedDuration human-readable recommended visit duration
     *                            (e.g. {@code "2-3 hours"})
     */
    record Response(
            String description,
            List<String> tips,
            List<String> mustSee,
            String localFood,
            String recommendedDuration
    ) {
        public Response {
            tips    = tips    == null ? List.of() : List.copyOf(tips);
            mustSee = mustSee == null ? List.of() : List.copyOf(mustSee);
        }
    }
}
