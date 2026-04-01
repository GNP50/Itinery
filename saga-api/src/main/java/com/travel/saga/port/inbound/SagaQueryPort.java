package com.travel.saga.port.inbound;

import com.travel.saga.dto.SagaInstanceDto;
import com.travel.saga.dto.SagaListRequest;
import com.travel.saga.dto.SagaStateTransitionDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Inbound port for querying saga instances.
 * Implemented by saga-impl-core.
 */
public interface SagaQueryPort {

    /**
     * Get a saga instance by its ID.
     */
    Optional<SagaInstanceDto> getSagaInstance(UUID sagaId);

    /**
     * Get saga instances by itinerary ID.
     */
    List<SagaInstanceDto> getSagasByItinerary(UUID itineraryId);

    /**
     * List saga instances with filters and pagination.
     */
    List<SagaInstanceDto> listSagaInstances(SagaListRequest request);

    /**
     * Count total saga instances matching the filter.
     */
    long countSagaInstances(SagaListRequest request);

    /**
     * Get the state transition history for a saga instance.
     * Note: This may require additional persistence of state transitions.
     */
    List<SagaStateTransitionDto> getSagaHistory(UUID sagaId);
}
