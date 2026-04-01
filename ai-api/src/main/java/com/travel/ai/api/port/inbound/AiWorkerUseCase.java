package com.travel.ai.api.port.inbound;

import java.util.UUID;

/**
 * Use-case port for AI enrichment worker operations.
 * <p>
 * Enriches all steps of an itinerary with AI-generated descriptions and tips.
 */
public interface AiWorkerUseCase {

    /**
     * Enriches all steps of the given itinerary with AI-generated content.
     *
     * @param itineraryId UUID of the itinerary to process
     * @param preferencesJson optional JSON blob from user preferences; may be null
     * @throws IllegalStateException if the itinerary is not found
     */
    void processAiEnrichment(UUID itineraryId, String preferencesJson);
}
