package com.travel.itinerary.impl.adapter.inbound.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.itinerary.api.kafka.SagaCompletedEvent;
import com.travel.itinerary.api.kafka.SagaFailedEvent;
import com.travel.itinerary.api.port.inbound.MarkItineraryStatusUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka listener for saga lifecycle events.
 * <p>
 * Consumes saga completion and failure events published by the saga orchestrator
 * and updates itinerary status using proper port/adapter pattern.
 * This maintains hexagonal architecture by avoiding direct database access
 * from the saga orchestrator.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SagaLifecycleListener {

    private final MarkItineraryStatusUseCase markItineraryStatusUseCase;
    private final ObjectMapper objectMapper;

    /**
     * Handles saga completion events.
     * Marks the itinerary as COMPLETED using proper domain logic.
     */
    @KafkaListener(topics = "saga.completed", groupId = "itinerary-saga-lifecycle")
    public void handleSagaCompleted(String payload) {
        SagaCompletedEvent event = parseCompleted(payload);
        if (event == null) return;

        log.info("SagaLifecycleListener: received saga.completed for itinerary id={}, saga id={}",
                 event.itineraryId(), event.sagaId());
        try {
            markItineraryStatusUseCase.markCompleted(event.itineraryId());
            log.info("SagaLifecycleListener: marked itinerary id={} as COMPLETED",
                     event.itineraryId());
        } catch (Exception ex) {
            log.error("SagaLifecycleListener: failed to mark itinerary id={} as COMPLETED: {}",
                      event.itineraryId(), ex.getMessage(), ex);
        } catch (Throwable ex) {
            log.error("SagaLifecycleListener: failed to mark itinerary id={} as COMPLETED: {}",
                    event.itineraryId(), ex.getMessage(), ex);

        }
    }

    /**
     * Handles saga failure events.
     * Marks the itinerary as FAILED using proper domain logic.
     */
    @KafkaListener(topics = "saga.failed", groupId = "itinerary-saga-lifecycle")
    public void handleSagaFailed(String payload) {
        SagaFailedEvent event = parseFailed(payload);
        if (event == null) return;

        log.info("SagaLifecycleListener: received saga.failed for itinerary id={}, saga id={}, step={}, reason={}",
                 event.itineraryId(), event.sagaId(), event.failedStep(), event.reason());
        try {
            markItineraryStatusUseCase.markFailed(event.itineraryId(), event.failedStep(), event.reason());
            log.info("SagaLifecycleListener: marked itinerary id={} as FAILED",
                     event.itineraryId());
        } catch (Throwable ex) {
            log.error("SagaLifecycleListener: failed to mark itinerary id={} as FAILED: {}",
                      event.itineraryId(), ex.getMessage(), ex);
        }
    }

    private SagaCompletedEvent parseCompleted(String payload) {
        try {
            return objectMapper.readValue(payload, SagaCompletedEvent.class);
        } catch (Exception ex) {
            log.error("SagaLifecycleListener: failed to parse saga.completed event: {}", ex.getMessage());
            return null;
        }
    }

    private SagaFailedEvent parseFailed(String payload) {
        try {
            return objectMapper.readValue(payload, SagaFailedEvent.class);
        } catch (Exception ex) {
            log.error("SagaLifecycleListener: failed to parse saga.failed event: {}", ex.getMessage());
            return null;
        }
    }
}
