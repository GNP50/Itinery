package com.travel.saga.adapter;

import com.travel.saga.domain.SagaInstance;
import com.travel.saga.port.outbound.SagaPersistencePort;
import com.travel.saga.repository.SagaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA adapter implementing SagaPersistencePort.
 * Bridges between the domain layer and JPA repositories.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SagaJpaAdapter implements SagaPersistencePort<SagaInstance> {

    private final SagaRepository sagaRepository;

    @Override
    public SagaInstance save(SagaInstance sagaInstance) {
        log.debug("Saving saga instance: {}", sagaInstance.getId());
        return sagaRepository.save(sagaInstance);
    }

    @Override
    public Optional<SagaInstance> findById(UUID sagaId) {
        log.debug("Finding saga instance by ID: {}", sagaId);
        return sagaRepository.findById(sagaId);
    }

    @Override
    public List<SagaInstance> findByItineraryId(UUID itineraryId) {
        log.debug("Finding saga instances by itinerary ID: {}", itineraryId);
        return sagaRepository.findByItineraryId(itineraryId);
    }

    @Override
    public List<SagaInstance> findAll(int offset, int limit) {
        log.debug("Finding all saga instances with offset: {} and limit: {}", offset, limit);
        // Use paginated query to avoid loading all records from the database
        var pageable = PageRequest.of(offset / limit, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        return sagaRepository.findAllWithCompletedSteps(pageable);
    }

    @Override
    public List<SagaInstance> findByCurrentState(String state, int offset, int limit) {
        log.debug("Finding saga instances by state: {} with offset: {} and limit: {}", state, offset, limit);
        var pageable = PageRequest.of(offset / limit, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        return sagaRepository.findByCurrentState(state, pageable);
    }

    @Override
    public long count() {
        return sagaRepository.count();
    }

    @Override
    public long countByCurrentState(String state) {
        return sagaRepository.countByCurrentState(state);
    }

    @Override
    public long countByCurrentStateIn(List<String> states) {
        log.debug("Counting saga instances by states: {}", states);
        return sagaRepository.countByCurrentStateIn(states);
    }

    @Override
    public List<SagaInstance> findQueuedByPriority(int limit) {
        log.debug("Finding queued saga instances ordered by priority, limit: {}", limit);
        var pageable = PageRequest.of(0, limit);
        return sagaRepository.findQueuedByPriority(pageable);
    }
}
