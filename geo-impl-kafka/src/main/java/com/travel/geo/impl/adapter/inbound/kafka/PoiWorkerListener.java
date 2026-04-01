package com.travel.geo.impl.adapter.inbound.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.geo.api.port.inbound.PoiWorkerUseCase;
import com.travel.itinerary.api.kafka.WorkerCompleteEvent;
import com.travel.itinerary.api.kafka.WorkerFailedEvent;
import com.travel.itinerary.api.kafka.WorkerRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka worker listener for the <b>POI</b> discovery stage.
 *
 * <p>Listens to {@code poi.worker.request}, discovers points of interest,
 * marks the itinerary {@code COMPLETED}, and publishes {@code poi.completed}
 * or {@code poi.failed}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PoiWorkerListener {

    private final PoiWorkerUseCase            poiWorkerUseCase;
    private final KafkaTemplate<String,String> kafkaTemplate;
    private final ObjectMapper                objectMapper;

    @KafkaListener(
        topics = {
            "poi.worker.request.admin",       // Priority 2 - consumed first
            "poi.worker.request.registered",  // Priority 1
            "poi.worker.request.anonymous"    // Priority 0 - consumed last
        },
        groupId = "poi-worker"
    )
    public void handlePoiRequest(String payload) {
        WorkerRequestEvent event = parse(payload);
        if (event == null) return;

        log.info("PoiWorker: received request for itinerary id={}", event.itineraryId());
        try {
            poiWorkerUseCase.processPoiDiscovery(event.itineraryId());
            publish("poi.completed", new WorkerCompleteEvent(event.itineraryId(), "POI"));
            log.info("PoiWorker: completed for itinerary id={}", event.itineraryId());
        } catch (Exception ex) {
            log.error("PoiWorker: failed for itinerary id={}: {}", event.itineraryId(), ex.getMessage(), ex);
            publish("poi.failed", new WorkerFailedEvent(event.itineraryId(), "POI", ex.getMessage()));
        }
    }

    private WorkerRequestEvent parse(String payload) {
        try {
            return objectMapper.readValue(payload, WorkerRequestEvent.class);
        } catch (Exception ex) {
            log.error("PoiWorker: failed to parse payload: {}", ex.getMessage());
            return null;
        }
    }

    private void publish(String topic, Object value) {
        try {
            kafkaTemplate.send(topic, objectMapper.writeValueAsString(value));
        } catch (Exception ex) {
            log.error("PoiWorker: failed to publish to {}: {}", topic, ex.getMessage());
        }
    }
}
