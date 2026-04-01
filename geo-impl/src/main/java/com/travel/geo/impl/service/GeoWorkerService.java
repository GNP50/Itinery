package com.travel.geo.impl.service;

import com.travel.geo.api.port.inbound.GeoWorkerUseCase;
import com.travel.geo.api.port.outbound.ItineraryStepDataPort;
import com.travel.itinerary.api.port.outbound.GeocodingPort;
import com.travel.itinerary.api.vo.GeoResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service implementation for geocoding worker operations.
 * <p>
 * Geocodes all steps of an itinerary by calling the GeocodingPort
 * and writing results back via ItineraryStepDataPort.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeoWorkerService implements GeoWorkerUseCase {

    private final GeocodingPort geocodingPort;
    private final ItineraryStepDataPort itineraryStepDataPort;

    @Override
    @Transactional
    public void processGeocoding(UUID itineraryId) {
        log.info("Starting geocoding processing for itinerary id={}", itineraryId);

        // Mark itinerary as processing
        itineraryStepDataPort.markItineraryProcessing(itineraryId);

        // Load all steps
        List<ItineraryStepDataPort.StepData> steps = itineraryStepDataPort.findStepsByItineraryId(itineraryId);

        // Geocode each step
        for (ItineraryStepDataPort.StepData step : steps) {
            if (step.placeName() == null || step.placeName().isBlank()) {
                log.debug("Step id={} has no placeName – skipping geocoding", step.id());
                continue;
            }

            try {
                GeoResult geoResult = geocodingPort.geocode(step.placeName(), null);
                itineraryStepDataPort.updateStepWithGeocodingResult(step.id(), geoResult);
                log.debug("Geocoded step id={} → lat={} lon={}", step.id(), geoResult.lat(), geoResult.lon());
            } catch (GeocodingPort.GeocodingException ex) {
                log.warn("Geocoding failed for step id={} placeName='{}': {}",
                        step.id(), step.placeName(), ex.getMessage());
            }
        }

        log.info("Geocoding processing DONE for itinerary id={}", itineraryId);
    }
}
