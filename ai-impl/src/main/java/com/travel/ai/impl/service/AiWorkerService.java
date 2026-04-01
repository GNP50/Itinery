package com.travel.ai.impl.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.ai.api.port.AiEnrichmentPort;
import com.travel.ai.api.port.inbound.AiWorkerUseCase;
import com.travel.ai.api.port.outbound.ItineraryStepDataPort;
import com.travel.ai.api.vo.AiItinerarySuggestion;
import com.travel.ai.api.vo.AiStepEnrichment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service implementation for AI enrichment worker operations.
 * <p>
 * Enriches all steps of an itinerary with AI-generated descriptions and tips
 * by calling the AiEnrichmentPort and writing results back via ItineraryStepDataPort.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiWorkerService implements AiWorkerUseCase {

    private final AiEnrichmentPort aiEnrichmentPort;
    private final ItineraryStepDataPort itineraryStepDataPort;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void processAiEnrichment(UUID itineraryId, String preferencesJson) {
        log.info("Starting AI enrichment processing for itinerary id={}", itineraryId);

        // Load all steps
        List<ItineraryStepDataPort.StepData> steps = itineraryStepDataPort.findStepsByItineraryId(itineraryId);
        String travelMode = itineraryStepDataPort.getTravelMode(itineraryId);

        // Enrich each step with AI descriptions and tips
        for (ItineraryStepDataPort.StepData step : steps) {
            try {
                // Resolve interests: step preferences override trip preferences
                List<String> interests = resolveInterests(step.preferences(), preferencesJson);

                AiStepEnrichment enrichment = aiEnrichmentPort.enrichStep(
                        step.placeName(),
                        step.city(),
                        step.region(),
                        step.country(),
                        travelMode,
                        interests  // Use resolved interests
                );
                itineraryStepDataPort.updateStepWithAiEnrichment(step.id(), enrichment);
                log.debug("AI enrichment applied for step id={} with interests={}", step.id(), interests);
            } catch (Exception ex) {
                log.warn("AI enrichment failed for step id={}: {}", step.id(), ex.getMessage());
            }
        }

        // Itinerary-level AI suggestions (controlled by preference flag)
        if (itineraryStepDataPort.isAiTipsEnabled(itineraryId)) {
            try {
                String title = itineraryStepDataPort.getItineraryTitle(itineraryId);
                List<String> placeNames = steps.stream()
                        .map(ItineraryStepDataPort.StepData::placeName)
                        .toList();

                // Use trip-level interests for itinerary summary
                List<String> tripInterests = extractInterests(preferencesJson);

                AiItinerarySuggestion suggestion = aiEnrichmentPort.suggestForItinerary(
                        title,
                        placeNames,
                        travelMode != null ? travelMode : "CAR",
                        tripInterests
                );

                String aiSuggestionsJson = objectMapper.writeValueAsString(suggestion);
                itineraryStepDataPort.updateItineraryAiSuggestions(itineraryId, aiSuggestionsJson);

                log.debug("Itinerary-level AI suggestions generated for id={}", itineraryId);
            } catch (Exception ex) {
                log.warn("Itinerary-level AI suggestions failed for id={}: {}", itineraryId, ex.getMessage());
            }
        }

        log.info("AI enrichment processing DONE for itinerary id={}", itineraryId);
    }

    /**
     * Resolves interests for a step by checking step preferences first,
     * then falling back to trip preferences.
     *
     * @param stepPreferencesJson step-level preferences JSON (may be null)
     * @param tripPreferencesJson trip-level preferences JSON (may be null)
     * @return list of interests to use for AI enrichment
     */
    private List<String> resolveInterests(String stepPreferencesJson, String tripPreferencesJson) {
        // Try step-level preferences first
        List<String> stepInterests = extractInterests(stepPreferencesJson);
        if (!stepInterests.isEmpty()) {
            return stepInterests;
        }

        // Fall back to trip-level preferences
        return extractInterests(tripPreferencesJson);
    }

    /**
     * Extracts the interests list from preferences JSON.
     *
     * @param preferencesJson JSON string containing preferences
     * @return list of interest strings, or empty list if not present or parsing fails
     */
    private List<String> extractInterests(String preferencesJson) {
        if (preferencesJson == null || preferencesJson.isBlank()) {
            return List.of();
        }
        try {
            Map<String, Object> prefs = objectMapper.readValue(preferencesJson,
                    new TypeReference<Map<String, Object>>() {});
            Object interestsObj = prefs.get("interests");
            if (interestsObj instanceof List<?> list) {
                return list.stream()
                        .filter(item -> item instanceof String)
                        .map(item -> (String) item)
                        .toList();
            }
            return List.of();
        } catch (Exception ex) {
            log.warn("Failed to parse preferences JSON: {}", ex.getMessage());
            return List.of();
        }
    }
}
