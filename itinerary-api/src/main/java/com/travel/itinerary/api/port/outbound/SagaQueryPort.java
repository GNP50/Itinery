package com.travel.itinerary.api.port.outbound;

import com.travel.saga.dto.SagaInstanceDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for querying saga orchestration information.
 * <p>
 * This port is implemented by an adapter that connects to the saga service
 * (e.g., via gRPC, REST, or in-memory for testing).
 */
public interface SagaQueryPort {

    /**
     * Get all saga instances associated with a specific itinerary.
     *
     * @param itineraryId the itinerary UUID
     * @return list of saga instances
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

