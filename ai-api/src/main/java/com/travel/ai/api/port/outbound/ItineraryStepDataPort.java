package com.travel.ai.api.port.outbound;

import com.travel.ai.api.vo.AiStepEnrichment;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Outbound port for accessing and modifying itinerary step data.
 * <p>
 * This port abstracts the itinerary persistence layer, allowing AI domain
 * workers to read step data and write back enriched results without depending
 * on the itinerary-impl module.
 * <p>
 * In a monolith deployment, this is implemented by delegating to
 * ItineraryPersistenceAdapter. In a microservice deployment, this could be
 * implemented as a gRPC or REST client calling the itinerary service API.
 */
public interface ItineraryStepDataPort {

    /**
     * Retrieves all steps for the given itinerary.
     *
     * @param itineraryId UUID of the itinerary
     * @return list of step data, ordered by stepOrder
     */
    List<StepData> findStepsByItineraryId(UUID itineraryId);

    /**
     * Retrieves the travel mode for the given itinerary.
     *
     * @param itineraryId UUID of the itinerary
     * @return travel mode as a string (e.g., "CAR", "BICYCLE"), or null if not set
     */
    String getTravelMode(UUID itineraryId);

    /**
     * Retrieves the title of the given itinerary.
     *
     * @param itineraryId UUID of the itinerary
     * @return itinerary title
     */
    String getItineraryTitle(UUID itineraryId);

    /**
     * Updates a single step with AI enrichment results.
     *
     * @param stepId step UUID
     * @param enrichment AI enrichment result
     */
    void updateStepWithAiEnrichment(UUID stepId, AiStepEnrichment enrichment);

    /**
     * Stores itinerary-level AI suggestions as JSON.
     *
     * @param itineraryId UUID of the itinerary
     * @param aiSuggestionsJson JSON string of AI suggestions
     */
    void updateItineraryAiSuggestions(UUID itineraryId, String aiSuggestionsJson);

    /**
     * Checks if AI tips generation is enabled for the given itinerary.
     *
     * @param itineraryId UUID of the itinerary
     * @return true if generate_ai_tips preference is enabled
     */
    boolean isAiTipsEnabled(UUID itineraryId);

    /**
     * Immutable data carrier for itinerary step information needed by AI workers.
     *
     * @param id step UUID
     * @param stepOrder 1-based position in itinerary
     * @param placeName place name
     * @param city city component
     * @param region region component
     * @param country country component
     * @param latitude WGS-84 latitude
     * @param longitude WGS-84 longitude
     * @param preferences step-level preferences JSON (may be null)
     */
    record StepData(
            UUID id,
            int stepOrder,
            String placeName,
            String city,
            String region,
            String country,
            BigDecimal latitude,
            BigDecimal longitude,
            String preferences
    ) {}
}
