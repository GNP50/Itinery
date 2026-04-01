package com.travel.geo.impl.service;

import com.travel.geo.api.port.inbound.RouteWorkerUseCase;
import com.travel.geo.api.port.outbound.ItineraryStepDataPort;
import com.travel.itinerary.api.port.outbound.RoutingPort;
import com.travel.itinerary.api.vo.RouteSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Service implementation for routing worker operations.
 * <p>
 * Calculates routes between consecutive steps by calling the RoutingPort
 * and writing results back via ItineraryStepDataPort.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RouteWorkerService implements RouteWorkerUseCase {

    private final RoutingPort routingPort;
    private final ItineraryStepDataPort itineraryStepDataPort;

    @Override
    @Transactional
    public void processRouting(UUID itineraryId) {
        log.info("Starting routing processing for itinerary id={}", itineraryId);

        // Load all steps
        List<ItineraryStepDataPort.StepData> steps = itineraryStepDataPort.findStepsByItineraryId(itineraryId);
        String travelMode = itineraryStepDataPort.getTravelMode(itineraryId);
        if (travelMode == null) {
            travelMode = "CAR";
        }

        BigDecimal totalDistance = BigDecimal.ZERO;
        int totalDuration = 0;
        ItineraryStepDataPort.StepData previousStep = null;

        // Route between consecutive steps
        for (ItineraryStepDataPort.StepData step : steps) {
            if (previousStep != null) {
                if (previousStep.latitude() == null || previousStep.longitude() == null
                        || step.latitude() == null || step.longitude() == null) {
                    log.debug("Skipping routing for step id={} – missing coordinates", step.id());
                } else {
                    try {
                        RouteSegment route = routingPort.getRoute(
                                previousStep.latitude().doubleValue(),
                                previousStep.longitude().doubleValue(),
                                step.latitude().doubleValue(),
                                step.longitude().doubleValue(),
                                travelMode
                        );

                        itineraryStepDataPort.updateStepWithRoutingResult(step.id(), route);

                        if (route.distanceKm() != null) {
                            totalDistance = totalDistance.add(route.distanceKm());
                        }
                        if (route.durationMin() != null) {
                            totalDuration += route.durationMin();
                        }

                        log.debug("Routed step id={} → {}km {}min", step.id(), route.distanceKm(), route.durationMin());
                    } catch (RoutingPort.RoutingException ex) {
                        log.warn("Routing failed for step id={}: {}", step.id(), ex.getMessage());
                    }
                }
            }
            previousStep = step;
        }

        // Update totals on the itinerary
        itineraryStepDataPort.updateItineraryTotals(itineraryId, totalDistance, totalDuration);

        log.info("Routing processing DONE for itinerary id={} distanceKm={} durationMin={}",
                itineraryId, totalDistance, totalDuration);
    }
}
