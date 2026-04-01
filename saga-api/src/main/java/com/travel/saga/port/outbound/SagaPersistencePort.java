package com.travel.saga.port.outbound;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for saga persistence operations.
 * Implemented by saga-impl-jpa.
 *
 * Note: This port operates on domain entities from saga-impl-core,
 * not DTOs. The generic type T represents the SagaInstance entity.
 */
public interface SagaPersistencePort<T> {

    /**
     * Save or update a saga instance.
     */
    T save(T sagaInstance);

    /**
     * Find a saga instance by ID.
     */
    Optional<T> findById(UUID sagaId);

    /**
     * Find saga instances by itinerary ID.
     */
    List<T> findByItineraryId(UUID itineraryId);

    /**
     * Find all saga instances with pagination.
     */
    List<T> findAll(int offset, int limit);

    /**
     * Find saga instances by state.
     */
    List<T> findByCurrentState(String state, int offset, int limit);

    /**
     * Count total saga instances.
     */
    long count();

    /**
     * Count saga instances by state.
     */
    long countByCurrentState(String state);

    /**
     * Count saga instances with current state in the given list.
     * Used to determine how many sagas are actively processing.
     */
    long countByCurrentStateIn(List<String> states);

    /**
     * Find queued saga instances ordered by priority (DESC) and creation time (ASC).
     * Returns the highest priority sagas that are waiting to be processed.
     *
     * @param limit maximum number of results
     * @return list of queued sagas ordered by priority and creation time
     */
    List<T> findQueuedByPriority(int limit);
}
