package com.travel.saga.port.inbound;

import com.travel.saga.dto.SagaOperationResult;

import java.util.UUID;

/**
 * Inbound port for saga command operations.
 * Implemented by saga-impl-core.
 */
public interface SagaCommandPort {

    /**
     * Retry a failed saga instance.
     */
    SagaOperationResult retrySaga(UUID sagaId);

    /**
     * Trigger compensation (rollback) for a saga instance.
     */
    SagaOperationResult compensateSaga(UUID sagaId);
}
