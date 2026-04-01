package com.travel.itinerary.impl.adapter.outbound.persistence;

import com.travel.itinerary.impl.domain.ItineraryEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ItineraryEvent} audit-log entities.
 * <p>
 * All entries are immutable once persisted.  Deletion is handled via the
 * cascade on the parent {@link com.travel.itinerary.impl.domain.Itinerary}
 * rather than through this repository directly.
 * <p>
 * Package-private: only persistence adapters in this package may use it.
 */
interface EventJpaRepository extends JpaRepository<ItineraryEvent, UUID> {
}
