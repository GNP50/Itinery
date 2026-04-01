package com.travel.geo.impl.adapter.inbound.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.geo.api.port.inbound.RouteWorkerUseCase;
import com.travel.itinerary.api.kafka.WorkerCompleteEvent;
import com.travel.itinerary.api.kafka.WorkerFailedEvent;
import com.travel.itinerary.api.kafka.WorkerRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka worker listener for the <b>ROUTE</b> processing stage.
 *
 * <p>Listens to {@code route.worker.request}, calculates routes between
 * consecutive steps, and publishes {@code route.completed} or {@code route.failed}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RouteWorkerListener {

    private final RouteWorkerUseCase          routeWorkerUseCase;
    private final KafkaTemplate<String,String> kafkaTemplate;
    private final ObjectMapper                objectMapper;

    @KafkaListener(
        topics = {
            "route.worker.request.admin",       // Priority 2 - consumed first
            "route.worker.request.registered",  // Priority 1
            "route.worker.request.anonymous"    // Priority 0 - consumed last
        },
        groupId = "route-worker"
    )
    public void handleRouteRequest(String payload) {
        WorkerRequestEvent event = parse(payload);
        if (event == null) return;

        log.info("RouteWorker: received request for itinerary id={}", event.itineraryId());
        try {
            routeWorkerUseCase.processRouting(event.itineraryId());
            publish("route.completed", new WorkerCompleteEvent(event.itineraryId(), "ROUTE"));
            log.info("RouteWorker: completed for itinerary id={}", event.itineraryId());
        } catch (Exception ex) {
            log.error("RouteWorker: failed for itinerary id={}: {}", event.itineraryId(), ex.getMessage(), ex);
            publish("route.failed", new WorkerFailedEvent(event.itineraryId(), "ROUTE", ex.getMessage()));
        }
    }

    private WorkerRequestEvent parse(String payload) {
        try {
            return objectMapper.readValue(payload, WorkerRequestEvent.class);
        } catch (Exception ex) {
            log.error("RouteWorker: failed to parse payload: {}", ex.getMessage());
            return null;
        }
    }

    private void publish(String topic, Object value) {
        try {
            kafkaTemplate.send(topic, objectMapper.writeValueAsString(value));
        } catch (Exception ex) {
            log.error("RouteWorker: failed to publish to {}: {}", topic, ex.getMessage());
        }
    }
}
