package com.travel.ai.impl.adapter.inbound.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.ai.api.port.inbound.AiWorkerUseCase;
import com.travel.itinerary.api.kafka.WorkerCompleteEvent;
import com.travel.itinerary.api.kafka.WorkerFailedEvent;
import com.travel.itinerary.api.kafka.WorkerRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka worker listener for the <b>AI</b> enrichment stage.
 *
 * <p>Listens to {@code ai.worker.request}, enriches steps with AI descriptions
 * and tips (using preferences from the request), and publishes
 * {@code ai.completed} or {@code ai.failed}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiWorkerListener {

    private final AiWorkerUseCase             aiWorkerUseCase;
    private final KafkaTemplate<String,String> kafkaTemplate;
    private final ObjectMapper                objectMapper;

    @KafkaListener(
        topics = {
            "ai.worker.request.admin",       // Priority 2 - consumed first
            "ai.worker.request.registered",  // Priority 1
            "ai.worker.request.anonymous"    // Priority 0 - consumed last
        },
        groupId = "ai-worker"
    )
    public void handleAiRequest(String payload) {
        WorkerRequestEvent event = parse(payload);
        if (event == null) return;

        log.info("AiWorker: received request for itinerary id={}", event.itineraryId());
        try {
            aiWorkerUseCase.processAiEnrichment(event.itineraryId(), event.preferencesJson());
            publish("ai.completed", new WorkerCompleteEvent(event.itineraryId(), "AI"));
            log.info("AiWorker: completed for itinerary id={}", event.itineraryId());
        } catch (Exception ex) {
            log.error("AiWorker: failed for itinerary id={}: {}", event.itineraryId(), ex.getMessage(), ex);
            publish("ai.failed", new WorkerFailedEvent(event.itineraryId(), "AI", ex.getMessage()));
        }
    }

    private WorkerRequestEvent parse(String payload) {
        try {
            return objectMapper.readValue(payload, WorkerRequestEvent.class);
        } catch (Exception ex) {
            log.error("AiWorker: failed to parse payload: {}", ex.getMessage());
            return null;
        }
    }

    private void publish(String topic, Object value) {
        try {
            kafkaTemplate.send(topic, objectMapper.writeValueAsString(value));
        } catch (Exception ex) {
            log.error("AiWorker: failed to publish to {}: {}", topic, ex.getMessage());
        }
    }
}
