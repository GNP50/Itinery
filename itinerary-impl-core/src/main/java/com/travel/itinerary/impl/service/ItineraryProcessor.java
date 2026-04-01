package com.travel.itinerary.impl.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.ai.api.port.AiEnrichmentPort;
import com.travel.ai.api.vo.AiItinerarySuggestion;
import com.travel.ai.api.vo.AiStepEnrichment;
import com.travel.itinerary.api.event.ItineraryCompletedEvent;
import com.travel.itinerary.api.event.ItineraryFailedEvent;
import com.travel.itinerary.api.event.StepProcessedEvent;
import com.travel.itinerary.api.port.outbound.*;
import com.travel.itinerary.api.vo.GeoResult;
import com.travel.itinerary.api.vo.PoiResult;
import com.travel.itinerary.api.vo.RouteSegment;
import com.travel.itinerary.impl.domain.Itinerary;
import com.travel.itinerary.impl.domain.ItineraryStep;
import com.travel.itinerary.impl.domain.enums.ItineraryStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stateful processor that performs the full enrichment pipeline for a single
 * itinerary:
 * <ol>
 *   <li>Geocoding via Nominatim (updates lat/lon, city, region, country)</li>
 *   <li>Routing via OSRM (updates distance/duration/geometry from previous step)</li>
 *   <li>AI enrichment via Ollama (updates aiDescription, aiTips)</li>
 *   <li>POI discovery via Overpass (updates poiNearby)</li>
 * </ol>
 * Steps 3 and 4 are treated as best-effort: exceptions are caught, logged, and
 * processing continues so that a failed AI or POI call never blocks delivery of
 * the geocoded/routed itinerary.
 * <p>
 * Individual itinerary runs can be interrupted via {@link #interrupt(UUID)}.
 * The processor checks the interrupted flag between every pipeline stage.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItineraryProcessor {

    private final ItineraryPersistencePort    persistencePort;
    private final GeocodingPort               geocodingPort;
    private final RoutingPort                 routingPort;
    private final PoiDiscoveryPort            poiDiscoveryPort;
    private final AiEnrichmentPort            aiEnrichmentPort;
    private final EventPublisherPort          eventPublisher;
    private final ObjectMapper                objectMapper;

    /** Holds UUIDs of itineraries that have been asked to stop. */
    private final Set<UUID> interruptedIds = ConcurrentHashMap.newKeySet();

    /**
     * Self-reference injected through the Spring proxy so that {@code @Transactional}
     * on {@link #processPipeline} and {@link #markFailed} is actually applied.
     * Field (not constructor) injection with {@code @Lazy} avoids a circular
     * dependency at startup.
     */
    @Lazy
    @Autowired
    private ItineraryProcessor self;

    private static final int POI_RADIUS_METERS = 2_000;

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    /**
     * Run the complete enrichment pipeline for the itinerary identified by
     * {@code itineraryId}.
     * <p>
     * This method is intentionally <em>not</em> annotated with
     * {@code @Async} – it is invoked from the {@link QueueManager}'s executor,
     * which manages the threading.  Each step-level save is wrapped in its own
     * transaction to allow partial progress to be visible to status queries.
     *
     * @param itineraryId UUID of the itinerary to process
     */
    public void processItinerary(UUID itineraryId) {
        log.info("Starting processing for itinerary id={}", itineraryId);
        interruptedIds.remove(itineraryId);

        Itinerary itinerary = (Itinerary) persistencePort.findById(itineraryId).orElse(null);
        if (itinerary == null) {
            log.warn("Itinerary id={} not found – aborting processing", itineraryId);
            return;
        }

        // Mark as PROCESSING
        itinerary.setStatus(ItineraryStatus.PROCESSING);
        itinerary = (Itinerary) persistencePort.save(itinerary);

        try {
            self.processPipeline(itineraryId);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.info("Processing interrupted for itinerary id={}", itineraryId);
            interruptedIds.remove(itineraryId);
        } catch (Exception ex) {
            log.error("Fatal error during processing of itinerary id={}: {}", itineraryId, ex.getMessage(), ex);
            self.markFailed(itineraryId, ex.getMessage());
        } catch (Throwable ex) {
            log.error("Fatal error during processing of itinerary id={}: {}", itineraryId, ex.getMessage(), ex);
            self.markFailed(itineraryId, ex.getMessage());
        } finally {
            interruptedIds.remove(itineraryId);
        }
    }

    /**
     * Signal that the processing run for the given itinerary should stop after
     * the current pipeline stage completes.
     *
     * @param id UUID of the itinerary to interrupt
     */
    public void interrupt(UUID id) {
        interruptedIds.add(id);
        log.info("Interrupt requested for itinerary id={}", id);
    }

    // -------------------------------------------------------------------------
    // Pipeline
    // -------------------------------------------------------------------------

    /**
     * Executes the step-by-step enrichment pipeline and, if enabled, generates
     * itinerary-level AI suggestions.
     */
    @Transactional
    protected void processPipeline(UUID itineraryId) throws Throwable {
        Itinerary itinerary = (Itinerary) persistencePort.findById(itineraryId).orElseThrow(
            () -> new IllegalStateException("Itinerary id=" + itineraryId + " disappeared before pipeline start")
        );
        // Copy to a plain ArrayList to avoid ConcurrentModificationException:
        // Hibernate may modify the PersistentBag internals during flush (triggered
        // by save() inside the loop), which would invalidate a live iterator.
        List<ItineraryStep> steps = new java.util.ArrayList<>(itinerary.getSteps());
        BigDecimal totalDistance = BigDecimal.ZERO;
        int        totalDuration = 0;
        ItineraryStep previousStep = null;

        String travelMode = itinerary.getTravelMode() != null
            ? itinerary.getTravelMode().name()
            : "CAR";

        for (ItineraryStep step : steps) {
            checkInterrupted(itinerary.getId());

            // 1. Geocoding
            geocodeStep(step);

            checkInterrupted(itinerary.getId());

            // 2. Routing (skip for the first step)
            if (previousStep != null) {
                RouteSegment route = routeStep(step, previousStep, travelMode);
                if (route != null) {
                    step.setDistanceFromPrevKm(route.distanceKm());
                    step.setDurationFromPrevMin(route.durationMin());
                    step.setRouteGeometryFromPrev(route.geoJsonGeometry());

                    if (route.distanceKm() != null) {
                        totalDistance = totalDistance.add(route.distanceKm());
                    }
                    if (route.durationMin() != null) {
                        totalDuration += route.durationMin();
                    }
                }
            }

            checkInterrupted(itinerary.getId());

            // 3. AI enrichment (best-effort)
            try {
                enrichStepWithAi(step, itinerary);
            } catch (Exception ex) {
                log.warn("AI enrichment failed for step id={}: {}", step.getId(), ex.getMessage());
            }

            checkInterrupted(itinerary.getId());

            // 4. POI discovery (best-effort)
            try {
                discoverPoi(step);
            } catch (Exception ex) {
                log.warn("POI discovery failed for step id={}: {}", step.getId(), ex.getMessage());
            }

            // Persist step progress
            persistencePort.save(itinerary);

            // Publish StepProcessedEvent
            eventPublisher.publish(new StepProcessedEvent(
                itinerary.getId(),
                step.getId(),
                step.getStepOrder(),
                step.getPlaceName(),
                step.getLatitude(),
                step.getLongitude(),
                step.getDistanceFromPrevKm(),
                UUID.randomUUID().toString(),
                Instant.now()
            ));

            previousStep = step;
        }

        // Store computed totals
        itinerary.setTotalDistanceKm(totalDistance);
        itinerary.setTotalDurationMinutes(totalDuration);

        // Itinerary-level AI suggestions (best-effort, controlled by preference flag)
        if (isAiTipsEnabled(itinerary)) {
            try {
                List<String> placeNames = steps.stream()
                    .map(ItineraryStep::getPlaceName)
                    .toList();
                AiItinerarySuggestion suggestion = aiEnrichmentPort.suggestForItinerary(
                    itinerary.getTitle(),
                    placeNames,
                    travelMode,
                    List.of()
                );
                itinerary.setAiSuggestions(objectMapper.writeValueAsString(suggestion));
            } catch (Exception ex) {
                log.warn("Itinerary-level AI suggestions failed for id={}: {}", itinerary.getId(), ex.getMessage());
            }
        }

        // Mark COMPLETED
        itinerary.setStatus(ItineraryStatus.COMPLETED);
        itinerary.setQueuePosition(null);
        persistencePort.save(itinerary);

        eventPublisher.publish(new ItineraryCompletedEvent(
            itinerary.getId(),
            itinerary.getTitle(),
            totalDistance,
            totalDuration,
            steps.size(),
            UUID.randomUUID().toString(),
            Instant.now()
        ));

        log.info("Processing COMPLETED for itinerary id={} steps={} distanceKm={} durationMin={}",
                 itinerary.getId(), steps.size(), totalDistance, totalDuration);
    }

    // =========================================================================
    // Markdown builder (used by processPipeline for documentation generation)
    // =========================================================================

    private String buildMarkdown(Itinerary itinerary, List<ItineraryStep> steps) {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(itinerary.getTitle()).append("\n\n");

        if (itinerary.getDescription() != null && !itinerary.getDescription().isBlank()) {
            md.append(itinerary.getDescription()).append("\n\n");
        }

        md.append("## Overview\n\n");
        if (itinerary.getTravelMode() != null) {
            md.append("- **Travel Mode**: ").append(itinerary.getTravelMode().name()).append("\n");
        }
        if (itinerary.getTotalDistanceKm() != null) {
            md.append("- **Total Distance**: ").append(itinerary.getTotalDistanceKm()).append(" km\n");
        }
        if (itinerary.getTotalDurationMinutes() != null) {
            int hours = itinerary.getTotalDurationMinutes() / 60;
            int mins  = itinerary.getTotalDurationMinutes() % 60;
            md.append("- **Estimated Duration**: ").append(hours).append("h ").append(mins).append("min\n");
        }
        md.append("\n");

        md.append("## Day-by-Day Plan\n\n");
        for (int i = 0; i < steps.size(); i++) {
            ItineraryStep step = steps.get(i);
            md.append("### Stop ").append(i + 1).append(": ").append(step.getPlaceName()).append("\n\n");

            if (step.getCity() != null || step.getCountry() != null) {
                md.append("**Location**: ");
                if (step.getCity() != null) md.append(step.getCity());
                if (step.getCountry() != null) md.append(", ").append(step.getCountry());
                md.append("\n\n");
            }

            if (step.getAiDescription() != null) {
                md.append(step.getAiDescription()).append("\n\n");
            }

            if (step.getAiTips() != null && !step.getAiTips().isBlank()) {
                md.append("**Travel Tips**: ").append(step.getAiTips()).append("\n\n");
            }

            if (step.getDistanceFromPrevKm() != null && i > 0) {
                md.append("*Distance from previous stop: ").append(step.getDistanceFromPrevKm())
                  .append(" km");
                if (step.getDurationFromPrevMin() != null) {
                    md.append(" (~").append(step.getDurationFromPrevMin()).append(" min)");
                }
                md.append("*\n\n");
            }
        }

        return md.toString();
    }

    // =========================================================================
    // Helper: load itinerary or throw
    // =========================================================================

    private Itinerary requireItinerary(UUID itineraryId) throws Throwable {
        return (Itinerary) persistencePort.findById(itineraryId)
            .orElseThrow(() -> new IllegalStateException("Itinerary not found: " + itineraryId));
    }

    // -------------------------------------------------------------------------
    // Per-step pipeline stages
    // -------------------------------------------------------------------------

    /**
     * Geocodes the step's {@code placeName} and applies the resolved address
     * fields back to the step entity.
     */
    private void geocodeStep(ItineraryStep step) {
        if (step.getPlaceName() == null || step.getPlaceName().isBlank()) {
            log.debug("Step id={} has no placeName – skipping geocoding", step.getId());
            return;
        }
        try {
            GeoResult geo = geocodingPort.geocode(step.getPlaceName(), null);
            step.setLatitude(geo.lat());
            step.setLongitude(geo.lon());
            step.setOsmId(geo.osmId());
            step.setCity(geo.city());
            step.setProvince(geo.province());
            step.setRegion(geo.region());
            step.setCountry(geo.country());
            step.setCountryCode(geo.countryCode());
            // Keep the canonical display name if no manual name was set
            if (step.getPlaceName() == null || step.getPlaceName().isBlank()) {
                step.setPlaceName(geo.displayName());
            }
            log.debug("Geocoded step id={} → lat={} lon={}", step.getId(), geo.lat(), geo.lon());
        } catch (GeocodingPort.GeocodingException ex) {
            log.warn("Geocoding failed for step id={} placeName='{}': {}",
                     step.getId(), step.getPlaceName(), ex.getMessage());
        }
    }

    /**
     * Calculates the route between the previous step and the current step.
     *
     * @return the route segment, or {@code null} if routing failed or coordinates
     *         are unavailable
     */
    private RouteSegment routeStep(ItineraryStep step, ItineraryStep prevStep, String travelMode) {
        if (prevStep.getLatitude() == null || prevStep.getLongitude() == null
                || step.getLatitude() == null || step.getLongitude() == null) {
            log.debug("Skipping routing for step id={} – missing coordinates", step.getId());
            return null;
        }
        try {
            return routingPort.getRoute(
                prevStep.getLatitude().doubleValue(),
                prevStep.getLongitude().doubleValue(),
                step.getLatitude().doubleValue(),
                step.getLongitude().doubleValue(),
                travelMode
            );
        } catch (RoutingPort.RoutingException ex) {
            log.warn("Routing failed for step id={}: {}", step.getId(), ex.getMessage());
            return null;
        }
    }

    /**
     * Calls the AI enrichment service and applies the description and tips to the
     * step entity.
     */
    private void enrichStepWithAi(ItineraryStep step, Itinerary itinerary) {
        AiStepEnrichment enrichment = aiEnrichmentPort.enrichStep(
            step.getPlaceName(),
            step.getCity(),
            step.getRegion(),
            step.getCountry(),
            itinerary.getTravelMode() != null ? itinerary.getTravelMode().name() : null,
            List.of()
        );
        step.setAiDescription(enrichment.description());
        if (enrichment.tips() != null && !enrichment.tips().isEmpty()) {
            try {
                step.setAiTips(objectMapper.writeValueAsString(enrichment.tips()));
            } catch (Exception ex) {
                step.setAiTips(String.join("; ", enrichment.tips()));
            }
        }
        log.debug("AI enrichment applied for step id={}", step.getId());
    }

    /**
     * Discovers POIs within {@value #POI_RADIUS_METERS} metres of the step and
     * stores the serialised list.
     */
    private void discoverPoi(ItineraryStep step) {
        if (step.getLatitude() == null || step.getLongitude() == null) {
            return;
        }
        List<PoiResult> pois = poiDiscoveryPort.findPoi(
            step.getLatitude().doubleValue(),
            step.getLongitude().doubleValue(),
            POI_RADIUS_METERS,
            null
        );
        if (pois != null && !pois.isEmpty()) {
            try {
                step.setPoiNearby(objectMapper.writeValueAsString(pois));
            } catch (Exception ex) {
                log.warn("Failed to serialise POI results for step id={}: {}", step.getId(), ex.getMessage());
            }
        }
        log.debug("POI discovery applied {} results for step id={}", pois != null ? pois.size() : 0, step.getId());
    }

    // -------------------------------------------------------------------------
    // Failure handling
    // -------------------------------------------------------------------------

    /**
     * Marks the itinerary as {@code FAILED} and publishes the failure event.
     */
    @Transactional
    protected void markFailed(UUID itineraryId, String reason) {
        try {
            Itinerary itinerary = (Itinerary) persistencePort.findById(itineraryId).orElse(null);
            if (itinerary == null) {
                log.error("Cannot mark FAILED – itinerary id={} not found", itineraryId);
                return;
            }
            itinerary.setStatus(ItineraryStatus.FAILED);
            itinerary.setQueuePosition(null);
            persistencePort.save(itinerary);
            eventPublisher.publish(new ItineraryFailedEvent(
                itinerary.getId(),
                itinerary.getTitle(),
                reason,
                UUID.randomUUID().toString(),
                Instant.now()
            ));
        } catch (Exception saveEx) {
            log.error("Could not save FAILED status for itinerary id={}: {}",
                      itineraryId, saveEx.getMessage(), saveEx);
        }
        log.error("Itinerary id={} marked as FAILED: {}", itineraryId, reason);
    }

    // -------------------------------------------------------------------------
    // Interrupt support
    // -------------------------------------------------------------------------

    /**
     * Checks whether processing has been requested to stop for the given id.
     *
     * @throws InterruptedException if an interrupt has been requested
     */
    private void checkInterrupted(UUID id) throws InterruptedException {
        if (interruptedIds.contains(id) || Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Processing interrupted for itinerary id=" + id);
        }
    }

    // -------------------------------------------------------------------------
    // Preference helper
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the {@code generate_ai_tips} preference is set to
     * {@code true} in the itinerary's preferences JSON blob.
     */
    @SuppressWarnings("unchecked")
    private boolean isAiTipsEnabled(Itinerary itinerary) {
        String prefsJson = itinerary.getPreferences();
        if (prefsJson == null || prefsJson.isBlank()) {
            return false;
        }
        try {
            Map<String, Object> prefs = objectMapper.readValue(prefsJson, Map.class);
            Object flag = prefs.get("generate_ai_tips");
            if (flag instanceof Boolean b) return b;
            if (flag instanceof String s) return Boolean.parseBoolean(s);
            return false;
        } catch (Exception ex) {
            log.warn("Could not parse preferences for itinerary id={}: {}", itinerary.getId(), ex.getMessage());
            return false;
        }
    }
}
