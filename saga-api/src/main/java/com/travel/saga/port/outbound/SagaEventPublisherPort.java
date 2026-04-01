package com.travel.saga.port.outbound;

import java.util.UUID;

/**
 * Outbound port for publishing saga lifecycle events.
 * Implemented by saga-impl-core (using Kafka).
 */
public interface SagaEventPublisherPort {

    /**
     * Publish saga started event.
     */
    void publishSagaStarted(UUID sagaId, UUID itineraryId);

    /**
     * Publish saga completed event.
     */
    void publishSagaCompleted(UUID sagaId, UUID itineraryId);

    /**
     * Publish saga failed event.
     */
    void publishSagaFailed(UUID sagaId, UUID itineraryId, String reason);

    /**
     * Publish saga compensated event.
     */
    void publishSagaCompensated(UUID sagaId, UUID itineraryId);
}
