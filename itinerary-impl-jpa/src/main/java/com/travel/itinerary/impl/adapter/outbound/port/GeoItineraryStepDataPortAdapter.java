package com.travel.itinerary.impl.adapter.outbound.port;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.geo.api.port.outbound.ItineraryStepDataPort;
import com.travel.itinerary.api.event.ItineraryCompletedEvent;
import com.travel.itinerary.api.port.outbound.EventPublisherPort;
import com.travel.itinerary.api.vo.GeoResult;
import com.travel.itinerary.api.vo.PoiResult;
import com.travel.itinerary.api.vo.RouteSegment;
import com.travel.itinerary.impl.adapter.outbound.persistence.ItineraryPersistenceAdapter;
import com.travel.itinerary.impl.domain.Itinerary;
import com.travel.itinerary.impl.domain.ItineraryStep;
import com.travel.itinerary.impl.domain.enums.ItineraryStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Adapter implementation of geo-api's ItineraryStepDataPort.
 * <p>
 * Provides geo domain workers access to itinerary step data by delegating
 * to ItineraryPersistenceAdapter. This adapter lives in itinerary-impl-jpa
 * so it can access the internal domain entities.
 */
@Slf4j
@Component("geoItineraryStepDataPortAdapter")
@RequiredArgsConstructor
public class GeoItineraryStepDataPortAdapter implements ItineraryStepDataPort {

    private final ItineraryPersistenceAdapter persistenceAdapter;
    private final EventPublisherPort eventPublisher;
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
                        step.getLatitude(),
                        step.getLongitude(),
                        step.getCity(),
                        step.getRegion(),
                        step.getCountry()
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
    @Transactional
    public void updateStepWithGeocodingResult(UUID stepId, GeoResult geoResult) {
        ItineraryStep step = findStepById(stepId);
        step.setLatitude(geoResult.lat());
        step.setLongitude(geoResult.lon());
        step.setOsmId(geoResult.osmId());
        step.setCity(geoResult.city());
        step.setProvince(geoResult.province());
        step.setRegion(geoResult.region());
        step.setCountry(geoResult.country());
        step.setCountryCode(geoResult.countryCode());
        if (step.getPlaceName() == null || step.getPlaceName().isBlank()) {
            step.setPlaceName(geoResult.displayName());
        }
        // Save is handled by JPA flush
        log.debug("Updated step id={} with geocoding result", stepId);
    }

    @Override
    @Transactional
    public void updateStepWithRoutingResult(UUID stepId, RouteSegment routeSegment) {
        ItineraryStep step = findStepById(stepId);
        step.setDistanceFromPrevKm(routeSegment.distanceKm());
        step.setDurationFromPrevMin(routeSegment.durationMin());
        step.setRouteGeometryFromPrev(routeSegment.geoJsonGeometry());
        log.debug("Updated step id={} with routing result", stepId);
    }

    @Override
    @Transactional
    public void updateStepWithPoiResults(UUID stepId, List<PoiResult> pois) {
        ItineraryStep step = findStepById(stepId);
        try {
            step.setPoiNearby(objectMapper.writeValueAsString(pois));
            log.debug("Updated step id={} with {} POI results", stepId, pois.size());
        } catch (Exception ex) {
            log.warn("Failed to serialize POI results for step id={}: {}", stepId, ex.getMessage());
        }
    }

    @Override
    @Transactional
    public void updateItineraryTotals(UUID itineraryId, BigDecimal totalDistanceKm, int totalDurationMin) {
        Itinerary itinerary = requireItinerary(itineraryId);
        itinerary.setTotalDistanceKm(totalDistanceKm);
        itinerary.setTotalDurationMinutes(totalDurationMin);
        log.debug("Updated itinerary id={} totals: {}km, {}min", itineraryId, totalDistanceKm, totalDurationMin);
    }

    @Override
    @Transactional
    public void markItineraryCompleted(UUID itineraryId) {
        Itinerary itinerary = requireItinerary(itineraryId);
        itinerary.setStatus(ItineraryStatus.COMPLETED);
        itinerary.setQueuePosition(null);
        persistenceAdapter.save(itinerary);

        List<ItineraryStep> steps = new ArrayList<>(itinerary.getSteps());

        eventPublisher.publish(new ItineraryCompletedEvent(
                itinerary.getId(),
                itinerary.getTitle(),
                itinerary.getTotalDistanceKm() != null ? itinerary.getTotalDistanceKm() : BigDecimal.ZERO,
                itinerary.getTotalDurationMinutes() != null ? itinerary.getTotalDurationMinutes() : 0,
                steps.size(),
                UUID.randomUUID().toString(),
                Instant.now()
        ));

        log.info("Marked itinerary id={} as COMPLETED", itineraryId);
    }

    @Override
    @Transactional
    public void markItineraryProcessing(UUID itineraryId) {
        Itinerary itinerary = requireItinerary(itineraryId);
        itinerary.setStatus(ItineraryStatus.PROCESSING);
        persistenceAdapter.save(itinerary);
        log.debug("Marked itinerary id={} as PROCESSING", itineraryId);
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
