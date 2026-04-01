package com.travel.ai.api.port;

import com.travel.ai.api.vo.AiItinerarySuggestion;
import com.travel.ai.api.vo.AiStepEnrichment;

import java.util.List;

/**
 * Outbound SPI: AI-powered enrichment of individual steps and complete
 * itineraries.
 * <p>
 * Implementations may call OpenAI, Anthropic, a local LLM, or any compatible
 * generative AI service.
 */
public interface AiEnrichmentPort {

    /**
     * Generate a rich description and contextual tips for a single itinerary step.
     *
     * @param placeName   canonical name of the place; must not be {@code null}
     * @param city        city the place belongs to; may be {@code null}
     * @param region      region or state the place belongs to; may be {@code null}
     * @param country     full country name; may be {@code null}
     * @param travelMode  mode of transport used to arrive at this stop; may be {@code null}
     * @param interests   list of traveller interest tags used to personalise the
     *                    response (e.g. {@code ["art", "food"]}); may be {@code null}
     *                    or empty
     * @return {@link AiStepEnrichment} containing description, tips and
     *         recommendations; never {@code null}
     * @throws AiEnrichmentException if the AI service is unavailable or returns
     *                               an unusable response
     */
    AiStepEnrichment enrichStep(
            String placeName,
            String city,
            String region,
            String country,
            String travelMode,
            List<String> interests
    );

    /**
     * Generate a high-level summary and travel suggestions for an entire itinerary.
     *
     * @param title      itinerary title; must not be {@code null}
     * @param places     ordered list of place names in the itinerary; must not be
     *                   {@code null} or empty
     * @param travelMode mode of transport; may be {@code null}
     * @param interests  list of traveller interest tags; may be {@code null} or empty
     * @return {@link AiItinerarySuggestion} with a summary and general tips;
     *         never {@code null}
     * @throws AiEnrichmentException if the AI service is unavailable or returns
     *                               an unusable response
     */
    AiItinerarySuggestion suggestForItinerary(
            String title,
            List<String> places,
            String travelMode,
            List<String> interests
    );

    /**
     * Unchecked exception thrown when an AI enrichment request cannot be completed.
     */
    class AiEnrichmentException extends RuntimeException {

        public AiEnrichmentException(String message) {
            super(message);
        }

        public AiEnrichmentException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
