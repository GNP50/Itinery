package com.travel.saga.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.itinerary.api.kafka.SagaCompletedEvent;
import com.travel.itinerary.api.kafka.SagaFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Publishes saga lifecycle events to Kafka for consumption by the itinerary domain.
 * This maintains proper hexagonal architecture by avoiding direct JDBC updates
 * to the itinerary table from the saga orchestrator.
 *
 * <p>Topic conventions:
 * <ul>
 *   <li>{@code saga.completed} – saga successfully completed all steps</li>
 *   <li>{@code saga.failed}    – saga failed after max retries</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SagaLifecyclePublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Publishes a saga completion event.
     * The itinerary domain will consume this and mark the itinerary as COMPLETED
     * using proper port/adapter pattern.
     */
    public void publishSagaCompleted(UUID itineraryId, UUID sagaId) {
        try {
            SagaCompletedEvent event = new SagaCompletedEvent(itineraryId, sagaId);
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("saga.completed", itineraryId.toString(), json);
            log.info("SagaLifecyclePublisher: published saga.completed for itinerary id={}, saga id={}",
                     itineraryId, sagaId);
        } catch (Exception ex) {
            log.error("SagaLifecyclePublisher: failed to publish saga.completed for itinerary id={}: {}",
                      itineraryId, ex.getMessage(), ex);
        }
    }

    /**
     * Publishes a saga failure event.
     * The itinerary domain will consume this and mark the itinerary as FAILED
     * using proper port/adapter pattern.
     */
    public void publishSagaFailed(UUID itineraryId, UUID sagaId, String failedStep, String reason) {
        try {
            SagaFailedEvent event = new SagaFailedEvent(itineraryId, sagaId, failedStep, reason);
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("saga.failed", itineraryId.toString(), json);
            log.info("SagaLifecyclePublisher: published saga.failed for itinerary id={}, saga id={}, step={}",
                     itineraryId, sagaId, failedStep);
        } catch (Exception ex) {
            log.error("SagaLifecyclePublisher: failed to publish saga.failed for itinerary id={}: {}",
                      itineraryId, ex.getMessage(), ex);
        }
    }
}
