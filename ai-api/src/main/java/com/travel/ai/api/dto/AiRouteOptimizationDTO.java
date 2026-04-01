package com.travel.ai.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * DTOs for AI-driven itinerary route optimisation.
 * <p>
 * The AI service analyses the given place names, considers travel distance,
 * logical clustering and opening hours (where available) and returns a
 * re-ordered sequence with an explanation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface AiRouteOptimizationDTO {

    /**
     * Inbound optimisation request.
     *
     * @param itineraryId UUID of the itinerary to optimise; must not be {@code null}
     * @param accessToken caller's authentication token; must not be blank
     * @param placeNames  ordered list of place names as they currently appear in the
     *                    itinerary; must not be {@code null} or empty
     */
    record Request(
            @NotNull(message = "itineraryId must not be null")
            UUID itineraryId,

            @NotBlank(message = "accessToken must not be blank")
            String accessToken,

            @NotEmpty(message = "placeNames must not be empty")
            List<String> placeNames
    ) {
        public Request {
            placeNames = placeNames == null ? List.of() : List.copyOf(placeNames);
        }
    }

    /**
     * Outbound optimisation response.
     *
     * @param optimizedOrder     the re-ordered sequence of steps; never {@code null}
     * @param reasoning          natural-language explanation of why this order was chosen
     * @param estimatedTotalKm   AI estimate of total driving/walking distance for the
     *                           optimised route; may be {@code null} when unavailable
     */
    record Response(
            List<OptimizedStep> optimizedOrder,
            String reasoning,
            BigDecimal estimatedTotalKm
    ) {
        public Response {
            optimizedOrder = optimizedOrder == null ? List.of() : List.copyOf(optimizedOrder);
        }
    }

    /**
     * Describes the mapping from original position to optimised position for one step.
     *
     * @param originalIndex zero-based position in the original list
     * @param newIndex      zero-based position in the optimised list
     * @param placeName     canonical name of the place at this step
     */
    record OptimizedStep(
            int originalIndex,
            int newIndex,

            @NotBlank(message = "placeName must not be blank")
            String placeName
    ) {}
}
