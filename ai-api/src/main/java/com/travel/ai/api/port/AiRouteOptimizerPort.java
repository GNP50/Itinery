package com.travel.ai.api.port;

import com.travel.ai.api.dto.AiRouteOptimizationDTO;

/**
 * Outbound SPI: AI-driven reordering of itinerary steps for an optimal route.
 * <p>
 * Implementations send the list of place names to an AI or combinatorial
 * optimisation service and receive back a re-ordered sequence with an
 * explanatory rationale.
 */
public interface AiRouteOptimizerPort {

    /**
     * Compute an optimised visit order for the given places.
     *
     * @param request the optimisation request containing the itinerary ID and
     *                current ordered list of place names; must not be {@code null}
     * @return the optimised ordering with per-step mappings and a natural-language
     *         explanation; never {@code null}
     * @throws AiRouteOptimizationException if the service is unavailable or cannot
     *                                      produce a valid optimisation
     */
    AiRouteOptimizationDTO.Response optimize(AiRouteOptimizationDTO.Request request);

    /**
     * Unchecked exception thrown when an AI route optimisation request cannot be
     * completed.
     */
    class AiRouteOptimizationException extends RuntimeException {

        public AiRouteOptimizationException(String message) {
            super(message);
        }

        public AiRouteOptimizationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
