package com.travel.itinerary.impl.adapter.outbound.port;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.ai.api.port.outbound.ItineraryStepDataPort;
import com.travel.ai.api.vo.AiStepEnrichment;
import com.travel.itinerary.impl.adapter.outbound.persistence.ItineraryPersistenceAdapter;
import com.travel.itinerary.impl.domain.Itinerary;
import com.travel.itinerary.impl.domain.ItineraryStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adapter implementation of ai-api's ItineraryStepDataPort.
 * <p>
 * Provides AI domain workers access to itinerary step data by delegating
 * to ItineraryPersistenceAdapter. This adapter lives in itinerary-impl-jpa
 * so it can access the internal domain entities.
 */
@Slf4j
@Component("aiItineraryStepDataPortAdapter")
@RequiredArgsConstructor
public class AiItineraryStepDataPortAdapter implements ItineraryStepDataPort {

    private final ItineraryPersistenceAdapter persistenceAdapter;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<StepData> findStepsByItineraryId(UUID itineraryId) {
        Itinerary itinerary = requireItinerary(itineraryId);
        return itinerary.getSteps().stream()
                .map(step -> new StepData(
                        step.getId(),
                        step.getStepOrder(),
                        step.getPlaceName(),
                        step.getCity(),
                        step.getRegion(),
                        step.getCountry(),
                        step.getLatitude(),
                        step.getLongitude(),
                        step.getPreferences()
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public String getTravelMode(UUID itineraryId) {
        Itinerary itinerary = requireItinerary(itineraryId);
        return itinerary.getTravelMode() != null ? itinerary.getTravelMode().name() : null;
    }

    @Override
    @Transactional(readOnly = true)
    public String getItineraryTitle(UUID itineraryId) {
        Itinerary itinerary = requireItinerary(itineraryId);
        return itinerary.getTitle();
    }

    @Override
    @Transactional
    public void updateStepWithAiEnrichment(UUID stepId, AiStepEnrichment enrichment) {
        ItineraryStep step = findStepById(stepId);
        step.setAiDescription(enrichment.description());
        if (enrichment.tips() != null && !enrichment.tips().isEmpty()) {
            try {
                step.setAiTips(objectMapper.writeValueAsString(enrichment.tips()));
            } catch (Exception ex) {
                step.setAiTips(String.join("; ", enrichment.tips()));
            }
        }
        log.debug("Updated step id={} with AI enrichment", stepId);
    }

    @Override
    @Transactional
    public void updateItineraryAiSuggestions(UUID itineraryId, String aiSuggestionsJson) {
        Itinerary itinerary = requireItinerary(itineraryId);
        itinerary.setAiSuggestions(aiSuggestionsJson);
        log.debug("Updated itinerary id={} with AI suggestions", itineraryId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAiTipsEnabled(UUID itineraryId) {
        Itinerary itinerary = requireItinerary(itineraryId);
        String prefsJson = itinerary.getPreferences();
        if (prefsJson == null || prefsJson.isBlank()) {
            return false;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> prefs = objectMapper.readValue(prefsJson, Map.class);
            Object flag = prefs.get("generate_ai_tips");
            if (flag instanceof Boolean b) return b;
            if (flag instanceof String s) return Boolean.parseBoolean(s);
            return false;
        } catch (Exception ex) {
            log.warn("Could not parse preferences for itinerary id={}: {}", itineraryId, ex.getMessage());
            return false;
        }
    }

    private Itinerary requireItinerary(UUID itineraryId) {
        return persistenceAdapter.findById(itineraryId)
                .orElseThrow(() -> new IllegalStateException("Itinerary not found: " + itineraryId));
    }

    private ItineraryStep findStepById(UUID stepId) {
        // Query step directly by ID (optimized: single targeted query)
        return persistenceAdapter.findStepById(stepId)
                .orElseThrow(() -> new IllegalStateException("Step not found: " + stepId));
    }
}
