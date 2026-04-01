package com.travel.geo.impl.service;

import com.travel.geo.api.port.inbound.PoiWorkerUseCase;
import com.travel.geo.api.port.outbound.ItineraryStepDataPort;
import com.travel.itinerary.api.port.outbound.PoiDiscoveryPort;
import com.travel.itinerary.api.vo.PoiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service implementation for POI discovery worker operations.
 * <p>
 * Discovers POIs near each step by calling the PoiDiscoveryPort
 * and writing results back via ItineraryStepDataPort.
 * Also marks the itinerary as COMPLETED and publishes the completion event.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PoiWorkerService implements PoiWorkerUseCase {

    private static final int POI_RADIUS_METERS = 2_000;

    private final PoiDiscoveryPort poiDiscoveryPort;
    private final ItineraryStepDataPort itineraryStepDataPort;

    @Override
    @Transactional
    public void processPoiDiscovery(UUID itineraryId) {
        log.info("Starting POI discovery processing for itinerary id={}", itineraryId);

        // Load all steps
        List<ItineraryStepDataPort.StepData> steps = itineraryStepDataPort.findStepsByItineraryId(itineraryId);

        // Discover POIs for each step
        for (ItineraryStepDataPort.StepData step : steps) {
            if (step.latitude() == null || step.longitude() == null) {
                log.debug("Skipping POI discovery for step id={} – missing coordinates", step.id());
                continue;
            }

            try {
                List<PoiResult> pois = poiDiscoveryPort.findPoi(
                        step.latitude().doubleValue(),
                        step.longitude().doubleValue(),
                        POI_RADIUS_METERS,
                        null
                );

                if (pois != null && !pois.isEmpty()) {
                    itineraryStepDataPort.updateStepWithPoiResults(step.id(), pois);
                    log.debug("POI discovery applied {} results for step id={}", pois.size(), step.id());
                }
            } catch (Exception ex) {
                log.warn("POI discovery failed for step id={}: {}", step.id(), ex.getMessage());
            }
        }

        // Mark itinerary as fully completed and publish event
        itineraryStepDataPort.markItineraryCompleted(itineraryId);

        log.info("POI discovery processing DONE – itinerary id={} marked COMPLETED", itineraryId);
    }
}
