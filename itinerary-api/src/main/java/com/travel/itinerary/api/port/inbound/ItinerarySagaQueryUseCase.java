package com.travel.itinerary.api.port.inbound;

import com.travel.saga.dto.SagaInstanceDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Inbound port for querying saga orchestration lifecycle information
 * associated with itineraries.
 * <p>
 * This use case allows administrators to inspect the saga processing
 * state for debugging and monitoring purposes.
 */
public interface ItinerarySagaQueryUseCase {

    /**
     * Get all saga instances associated with a specific itinerary.
     * <p>
     * Multiple sagas may exist for a single itinerary if it was reprocessed
     * or if processing failed and was retried.
     *
     * @param itineraryId the itinerary UUID
     * @return list of saga instances (may be empty)
     */
    List<SagaInstanceDto> getSagasByItinerary(UUID itineraryId);

    /**
     * Get a specific saga instance by its ID.
     *
     * @param sagaId the saga UUID
     * @return the saga instance, or empty if not found
     */
    Optional<SagaInstanceDto> getSagaInstance(UUID sagaId);
}

