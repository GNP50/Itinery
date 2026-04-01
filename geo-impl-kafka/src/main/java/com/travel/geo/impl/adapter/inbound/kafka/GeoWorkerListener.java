package com.travel.geo.impl.adapter.inbound.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.geo.api.port.inbound.GeoWorkerUseCase;
import com.travel.itinerary.api.kafka.WorkerCompleteEvent;
import com.travel.itinerary.api.kafka.WorkerFailedEvent;
import com.travel.itinerary.api.kafka.WorkerRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka worker listener for the <b>GEO</b> processing stage.
 *
 * <p>Listens to {@code geo.worker.request}, geocodes all steps of the requested
 * itinerary, and publishes {@code geo.completed} or {@code geo.failed}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeoWorkerListener {

    private final GeoWorkerUseCase            geoWorkerUseCase;
    private final KafkaTemplate<String,String> kafkaTemplate;
    private final ObjectMapper                objectMapper;

    @KafkaListener(
        topics = {
            "geo.worker.request.admin",       // Priority 2 - consumed first
            "geo.worker.request.registered",  // Priority 1
            "geo.worker.request.anonymous"    // Priority 0 - consumed last
        },
        groupId = "geo-worker"
    )
    public void handleGeoRequest(String payload) {
        WorkerRequestEvent event = parse(payload);
        if (event == null) return;

        log.info("GeoWorker: received request for itinerary id={} (sagaVersion={})", 
                 event.itineraryId(), event.sagaVersion());
        try {
            geoWorkerUseCase.processGeocoding(event.itineraryId());
            publish("geo.completed", new WorkerCompleteEvent(event.itineraryId(), "GEO", event.sagaVersion()));
            log.info("GeoWorker: completed for itinerary id={} (sagaVersion={})", 
                     event.itineraryId(), event.sagaVersion());
        } catch (Exception ex) {
            log.error("GeoWorker: failed for itinerary id={} (sagaVersion={}): {}", 
                      event.itineraryId(), event.sagaVersion(), ex.getMessage(), ex);
            publish("geo.failed", new WorkerFailedEvent(event.itineraryId(), "GEO", ex.getMessage(), event.sagaVersion()));
        }
    }

    private WorkerRequestEvent parse(String payload) {
        try {
            return objectMapper.readValue(payload, WorkerRequestEvent.class);
        } catch (Exception ex) {
            log.error("GeoWorker: failed to parse payload: {}", ex.getMessage());
            return null;
        }
    }

    private void publish(String topic, Object value) {
        try {
            kafkaTemplate.send(topic, objectMapper.writeValueAsString(value));
        } catch (Exception ex) {
            log.error("GeoWorker: failed to publish to {}: {}", topic, ex.getMessage());
        }
    }
}
