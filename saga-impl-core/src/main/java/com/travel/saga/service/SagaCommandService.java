package com.travel.saga.service;

import com.travel.saga.domain.SagaInstance;
import com.travel.saga.dto.SagaOperationResult;
import com.travel.saga.port.inbound.SagaCommandPort;
import com.travel.saga.port.outbound.SagaPersistencePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Implementation of SagaCommandPort.
 * Provides command operations for saga instances.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class SagaCommandService implements SagaCommandPort {

    private final SagaOrchestrationService orchestrationService;
    private final SagaPersistencePort<SagaInstance> persistencePort;

    @Override
    public SagaOperationResult retrySaga(UUID sagaId) {
        log.info("Retrying saga: {}", sagaId);

        try {
            var sagaOpt = persistencePort.findById(sagaId);
            if (sagaOpt.isEmpty()) {
                return SagaOperationResult.failure("Saga not found: " + sagaId);
            }

            var saga = sagaOpt.get();
            if (!saga.canRetry()) {
                return SagaOperationResult.failure(
                    "Saga has reached max retries: " + saga.getRetryCount() + "/" + saga.getMaxRetries()
                );
            }

            orchestrationService.retrySaga(saga);
            return SagaOperationResult.success("Saga retry initiated");

        } catch (Exception e) {
            log.error("Failed to retry saga: {}", sagaId, e);
            return SagaOperationResult.failure("Failed to retry saga: " + e.getMessage());
        }
    }

    @Override
    public SagaOperationResult compensateSaga(UUID sagaId) {
        log.info("Compensating saga: {}", sagaId);

        try {
            var sagaOpt = persistencePort.findById(sagaId);
            if (sagaOpt.isEmpty()) {
                return SagaOperationResult.failure("Saga not found: " + sagaId);
            }

            orchestrationService.compensate(sagaId);
            return SagaOperationResult.success("Saga compensation initiated");

        } catch (Exception e) {
            log.error("Failed to compensate saga: {}", sagaId, e);
            return SagaOperationResult.failure("Failed to compensate saga: " + e.getMessage());
        }
    }
}
