package com.travel.saga.service;

import com.travel.saga.domain.SagaInstance;
import com.travel.saga.dto.SagaInstanceDto;
import com.travel.saga.dto.SagaListRequest;
import com.travel.saga.dto.SagaStateTransitionDto;
import com.travel.saga.port.inbound.SagaQueryPort;
import com.travel.saga.port.outbound.SagaPersistencePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of SagaQueryPort.
 * Provides query operations for saga instances.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SagaQueryService implements SagaQueryPort {

    private final SagaPersistencePort<SagaInstance> persistencePort;

    @Override
    public Optional<SagaInstanceDto> getSagaInstance(UUID sagaId) {
        log.debug("Getting saga instance: {}", sagaId);
        return persistencePort.findById(sagaId)
            .map(this::toDto);
    }

    @Override
    public List<SagaInstanceDto> getSagasByItinerary(UUID itineraryId) {
        log.debug("Getting sagas for itinerary: {}", itineraryId);
        return persistencePort.findByItineraryId(itineraryId).stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    @Override
    public List<SagaInstanceDto> listSagaInstances(SagaListRequest request) {
        log.debug("Listing saga instances with request: {}", request);

        List<SagaInstance> instances;
        if (request.stateFilter() != null) {
            instances = persistencePort.findByCurrentState(
                request.stateFilter(),
                request.offset(),
                request.limit()
            );
        } else if (request.itineraryId() != null) {
            instances = persistencePort.findByItineraryId(request.itineraryId());
        } else {
            instances = persistencePort.findAll(request.offset(), request.limit());
        }

        return instances.stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    @Override
    public long countSagaInstances(SagaListRequest request) {
        if (request.stateFilter() != null) {
            return persistencePort.countByCurrentState(request.stateFilter());
        }
        return persistencePort.count();
    }

    @Override
    public List<SagaStateTransitionDto> getSagaHistory(UUID sagaId) {
        // TODO: Implement state transition history tracking
        // This requires persisting state transitions in a separate table
        log.warn("getSagaHistory not yet implemented for saga: {}", sagaId);
        return List.of();
    }

    private SagaInstanceDto toDto(SagaInstance saga) {
        return SagaInstanceDto.builder()
            .id(saga.getId())
            .itineraryId(saga.getItineraryId())
            .currentState(saga.getCurrentState())
            .completedSteps(saga.getCompletedSteps())
            .failedStep(saga.getFailedStep())
            .errorMessage(saga.getErrorMessage())
            .version(saga.getVersion())
            .retryCount(saga.getRetryCount())
            .maxRetries(saga.getMaxRetries())
            .preferences(saga.getPreferences())
            .createdAt(saga.getCreatedAt())
            .updatedAt(saga.getUpdatedAt())
            .build();
    }
}
