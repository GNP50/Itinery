package com.travel.itinerary.impl.adapter.outbound.persistence;

import com.travel.itinerary.impl.domain.ItineraryStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ItineraryStep} entities.
 * <p>
 * Direct step access is rarely needed because steps are typically managed
 * through the {@link Itinerary} aggregate (cascade operations).  This
 * repository exists to support targeted queries that bypass the aggregate root
 * when performance requires it.
 * <p>
 * Package-private: only persistence adapters in this package may use it.
 */
interface StepJpaRepository extends JpaRepository<ItineraryStep, UUID> {
}
