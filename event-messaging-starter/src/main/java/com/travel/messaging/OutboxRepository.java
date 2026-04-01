package com.travel.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link OutboxEvent}.
 *
 * <p>The primary query used by the outbox poller fetches the oldest
 * un-published events in batches of 100, ordered by {@code createdAt}
 * to preserve causal ordering within a single aggregate.
 *
 * <h3>Locking strategy</h3>
 * <p>The polling query applies a {@code SKIP LOCKED} pessimistic write lock
 * so that multiple application instances can poll concurrently without
 * processing the same row twice.  This relies on the underlying database
 * (PostgreSQL 9.5+) supporting {@code SELECT … FOR UPDATE SKIP LOCKED}.
 */
@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Returns up to 100 unpublished outbox events ordered oldest-first.
     *
     * <p>The {@link Lock} + {@link QueryHints} combination translates to
     * {@code SELECT … FOR UPDATE SKIP LOCKED} in PostgreSQL, which prevents
     * two competing pollers from claiming the same row.
     *
     * @return a list of at most 100 {@link OutboxEvent} entities, never {@code null}
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
            @QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2") // SKIP LOCKED
    })
    List<OutboxEvent> findTop100ByPublishedFalseOrderByCreatedAtAsc();

    /**
     * Counts unpublished events – used by the Micrometer gauge to expose
     * outbox lag as a metric.
     *
     * @return number of rows where {@code published = false}
     */
    long countByPublishedFalse();
}
