package com.travel.saga.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.saga.service.SagaOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Kafka listener that receives domain events from the itinerary service and
 * delegates to {@link SagaOrchestrationService} to start / advance sagas.
 *
 * <p>The {@code itinerary.created} payload is the JSON-serialised
 * {@code ItineraryCreatedEvent} record published via the Transactional Outbox.
 * The listener extracts {@code id} and {@code preferencesJson} fields and calls
 * {@link SagaOrchestrationService#start}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventListener {

    private final SagaOrchestrationService sagaOrchestrationService;
    private final ObjectMapper             objectMapper;

    @KafkaListener(topics = "itinerary.created", groupId = "saga-orchestrator")
    public void handleItineraryCreated(String payload) {
        log.info("KafkaEventListener: received itinerary.created event");
        try {
            JsonNode root          = objectMapper.readTree(payload);
            UUID     itineraryId   = UUID.fromString(root.path("id").asText());
            String   preferences   = root.has("preferencesJson")
                                     ? root.get("preferencesJson").asText(null)
                                     : null;
            String   userType      = root.path("userType").asText("ANONYMOUS");

            log.debug("KafkaEventListener: itineraryId={}, userType={}", itineraryId, userType);
            sagaOrchestrationService.start(itineraryId, userType, preferences);
        } catch (Exception ex) {
            log.error("KafkaEventListener: failed to handle itinerary.created: {}", ex.getMessage(), ex);
        }
    }

    @KafkaListener(topics = "itinerary.updated", groupId = "saga-orchestrator")
    public void handleItineraryUpdated(String payload) {
        log.info("KafkaEventListener: received itinerary.updated event");
        try {
            JsonNode root          = objectMapper.readTree(payload);
            UUID     itineraryId   = UUID.fromString(root.path("id").asText());
            String   preferences   = root.has("preferencesJson")
                                     ? root.get("preferencesJson").asText(null)
                                     : null;
            String   userType      = root.path("userType").asText("ANONYMOUS");

            log.debug("KafkaEventListener: itineraryId={}, userType={}, restarting saga", itineraryId, userType);
            sagaOrchestrationService.restart(itineraryId, userType, preferences);
        } catch (Exception ex) {
            log.error("KafkaEventListener: failed to handle itinerary.updated: {}", ex.getMessage(), ex);
        }
    }

    @KafkaListener(topics = "itinerary.failed", groupId = "saga-orchestrator")
    public void handleItineraryFailed(String payload) {
        log.info("KafkaEventListener: received itinerary.failed event: {}", payload);
        // DLQ / monitoring – saga state is already updated by WorkerEventListener
    }

    @KafkaListener(topics = "itinerary.completed", groupId = "saga-orchestrator")
    public void handleItineraryCompleted(String payload) {
        log.debug("KafkaEventListener: received itinerary.completed event (informational)");
    }

    @KafkaListener(topics = "dlq.itinerary-events", groupId = "saga-dlq-listener")
    public void handleDLQMessage(String payload) {
        log.warn("KafkaEventListener: received DLQ message: {}", payload);
    }
}
