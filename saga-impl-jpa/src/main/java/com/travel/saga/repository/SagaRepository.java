package com.travel.saga.repository;

import com.travel.saga.domain.SagaInstance;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SagaRepository extends JpaRepository<SagaInstance, UUID> {

    /**
     * Find saga instance by ID with completedSteps eagerly loaded.
     * Uses LEFT JOIN FETCH to avoid LazyInitializationException.
     */
    @Query("SELECT s FROM SagaInstance s LEFT JOIN FETCH s.completedSteps WHERE s.id = :id")
    Optional<SagaInstance> findById(@Param("id") UUID id);

    /**
     * Find saga instances by itinerary ID with completedSteps eagerly loaded.
     * Uses LEFT JOIN FETCH to avoid LazyInitializationException when accessed outside transaction.
     */
    @Query("SELECT DISTINCT s FROM SagaInstance s LEFT JOIN FETCH s.completedSteps WHERE s.itineraryId = :itineraryId")
    List<SagaInstance> findByItineraryId(@Param("itineraryId") UUID itineraryId);

    /**
     * Find all saga instances with completedSteps eagerly loaded.
     */
    @Query("SELECT DISTINCT s FROM SagaInstance s LEFT JOIN FETCH s.completedSteps")
    List<SagaInstance> findAllWithCompletedSteps();

    /**
     * Find paginated saga instances with completedSteps eagerly loaded.
     * Avoids loading all records when only a page is needed.
     */
    @Query("SELECT DISTINCT s FROM SagaInstance s LEFT JOIN FETCH s.completedSteps ORDER BY s.createdAt DESC")
    List<SagaInstance> findAllWithCompletedSteps(Pageable pageable);

    /**
     * Find saga instances by current state with completedSteps eagerly loaded.
     * Uses JOIN FETCH to avoid N+1 queries when accessing completedSteps.
     */
    @Query("SELECT DISTINCT s FROM SagaInstance s LEFT JOIN FETCH s.completedSteps WHERE s.currentState = :currentState")
    List<SagaInstance> findByCurrentState(@Param("currentState") String currentState);

    /**
     * Find saga instances by current state with completedSteps eagerly loaded (paginated).
     * Uses JOIN FETCH to avoid N+1 queries when accessing completedSteps.
     */
    @Query("SELECT DISTINCT s FROM SagaInstance s LEFT JOIN FETCH s.completedSteps WHERE s.currentState = :currentState")
    List<SagaInstance> findByCurrentState(@Param("currentState") String currentState, Pageable pageable);

    long countByCurrentState(String currentState);

    /**
     * Count saga instances with current state in the given list.
     */
    long countByCurrentStateIn(List<String> currentStates);

    /**
     * Find QUEUED saga instances ordered by priority DESC and creation time ASC, with completedSteps eagerly loaded.
     * This ensures high-priority sagas are processed first, and within the same priority,
     * older sagas are processed first (FIFO). Uses JOIN FETCH to avoid N+1 queries.
     */
    @Query("SELECT DISTINCT s FROM SagaInstance s LEFT JOIN FETCH s.completedSteps WHERE s.currentState = 'QUEUED' " +
           "ORDER BY s.priority DESC, s.createdAt ASC")
    List<SagaInstance> findQueuedByPriority(Pageable pageable);

    List<SagaInstance> findByFailedStep(String failedStep);

    List<SagaInstance> findByCreatedAtAfter(java.time.Instant createdAt);

    List<SagaInstance> findByRetryCountGreaterThan(int retryCount);
}
