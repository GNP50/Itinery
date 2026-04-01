package com.travel.saga.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.itinerary.api.kafka.WorkerCompleteEvent;
import com.travel.itinerary.api.kafka.WorkerFailedEvent;
import com.travel.saga.service.SagaOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes worker completion and failure events from Kafka and delegates to
 * {@link SagaOrchestrationService} to advance (or retry/compensate) the saga.
 *
 * <p>Topics consumed:
 * <ul>
 *   <li>{@code geo.completed} / {@code geo.failed}</li>
 *   <li>{@code route.completed} / {@code route.failed}</li>
 *   <li>{@code ai.completed} / {@code ai.failed}</li>
 *   <li>{@code poi.completed} / {@code poi.failed}</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkerEventListener {

    private final SagaOrchestrationService sagaOrchestrationService;
    private final ObjectMapper             objectMapper;

    // ── Completion events ────────────────────────────────────────────────────

    @KafkaListener(topics = "geo.completed", groupId = "saga-orchestrator")
    public void onGeoCompleted(String payload) {
        WorkerCompleteEvent event = parseComplete(payload);
        if (event == null) return;
        log.info("WorkerEventListener: geo.completed for itinerary id={} (sagaVersion={})", 
                 event.itineraryId(), event.sagaVersion());
        sagaOrchestrationService.onGeoCompleted(event.itineraryId(), event.sagaVersion());
    }

    @KafkaListener(topics = "route.completed", groupId = "saga-orchestrator")
    public void onRouteCompleted(String payload) {
        WorkerCompleteEvent event = parseComplete(payload);
        if (event == null) return;
        log.info("WorkerEventListener: route.completed for itinerary id={} (sagaVersion={})", 
                 event.itineraryId(), event.sagaVersion());
        sagaOrchestrationService.onRouteCompleted(event.itineraryId(), event.sagaVersion());
    }

    @KafkaListener(topics = "ai.completed", groupId = "saga-orchestrator")
    public void onAiCompleted(String payload) {
        WorkerCompleteEvent event = parseComplete(payload);
        if (event == null) return;
        log.info("WorkerEventListener: ai.completed for itinerary id={} (sagaVersion={})", 
                 event.itineraryId(), event.sagaVersion());
        sagaOrchestrationService.onAiCompleted(event.itineraryId(), event.sagaVersion());
    }

    @KafkaListener(topics = "poi.completed", groupId = "saga-orchestrator")
    public void onPoiCompleted(String payload) {
        WorkerCompleteEvent event = parseComplete(payload);
        if (event == null) return;
        log.info("WorkerEventListener: poi.completed for itinerary id={} (sagaVersion={})", 
                 event.itineraryId(), event.sagaVersion());
        sagaOrchestrationService.onPoiCompleted(event.itineraryId(), event.sagaVersion());
    }

    // ── Failure events ───────────────────────────────────────────────────────

    @KafkaListener(topics = "geo.failed", groupId = "saga-orchestrator")
    public void onGeoFailed(String payload) {
        WorkerFailedEvent event = parseFailed(payload);
        if (event == null) return;
        log.warn("WorkerEventListener: geo.failed for itinerary id={} (sagaVersion={}): {}", 
                 event.itineraryId(), event.sagaVersion(), event.errorMessage());
        sagaOrchestrationService.onGeoFailed(event.itineraryId(), event.errorMessage(), event.sagaVersion());
    }

    @KafkaListener(topics = "route.failed", groupId = "saga-orchestrator")
    public void onRouteFailed(String payload) {
        WorkerFailedEvent event = parseFailed(payload);
        if (event == null) return;
        log.warn("WorkerEventListener: route.failed for itinerary id={} (sagaVersion={}): {}", 
                 event.itineraryId(), event.sagaVersion(), event.errorMessage());
        sagaOrchestrationService.onRouteFailed(event.itineraryId(), event.errorMessage(), event.sagaVersion());
    }

    @KafkaListener(topics = "ai.failed", groupId = "saga-orchestrator")
    public void onAiFailed(String payload) {
        WorkerFailedEvent event = parseFailed(payload);
        if (event == null) return;
        log.warn("WorkerEventListener: ai.failed for itinerary id={} (sagaVersion={}): {}", 
                 event.itineraryId(), event.sagaVersion(), event.errorMessage());
        sagaOrchestrationService.onAiFailed(event.itineraryId(), event.errorMessage(), event.sagaVersion());
    }

    @KafkaListener(topics = "poi.failed", groupId = "saga-orchestrator")
    public void onPoiFailed(String payload) {
        WorkerFailedEvent event = parseFailed(payload);
        if (event == null) return;
        log.warn("WorkerEventListener: poi.failed for itinerary id={} (sagaVersion={}): {}", 
                 event.itineraryId(), event.sagaVersion(), event.errorMessage());
        sagaOrchestrationService.onPoiFailed(event.itineraryId(), event.errorMessage(), event.sagaVersion());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private WorkerCompleteEvent parseComplete(String payload) {
        try {
            return objectMapper.readValue(payload, WorkerCompleteEvent.class);
        } catch (Exception ex) {
            log.error("WorkerEventListener: failed to parse complete event: {}", ex.getMessage());
            return null;
        }
    }

    private WorkerFailedEvent parseFailed(String payload) {
        try {
            return objectMapper.readValue(payload, WorkerFailedEvent.class);
        } catch (Exception ex) {
            log.error("WorkerEventListener: failed to parse failed event: {}", ex.getMessage());
            return null;
        }
    }
}
