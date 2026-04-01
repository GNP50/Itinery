package com.travel.itinerary.impl.service;

import com.travel.itinerary.api.event.ItineraryCompletedEvent;
import com.travel.itinerary.api.event.ItineraryFailedEvent;
import com.travel.itinerary.api.port.inbound.MarkItineraryStatusUseCase;
import com.travel.itinerary.api.port.outbound.EventPublisherPort;
import com.travel.itinerary.api.port.outbound.ItineraryPersistencePort;
import com.travel.itinerary.impl.domain.Itinerary;
import com.travel.itinerary.impl.domain.enums.ItineraryStatus;
import com.travel.queue.port.QueueManagementPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Service implementation for marking itinerary status based on saga lifecycle events.
 * This maintains proper hexagonal architecture by handling saga completion/failure
 * through domain logic and port/adapter pattern instead of direct database access.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarkItineraryStatusService implements MarkItineraryStatusUseCase {

    private final ItineraryPersistencePort persistencePort;
    private final EventPublisherPort eventPublisher;
    private final QueueManagementPort queueManagementPort;

    @Override
    @Transactional
    public void markCompleted(UUID itineraryId) throws Throwable {
        Itinerary itinerary = (Itinerary) persistencePort.findById(itineraryId)
                .orElseThrow(() -> new IllegalArgumentException("Itinerary not found: " + itineraryId));

        itinerary.setStatus(ItineraryStatus.COMPLETED);
        itinerary.setQueuePosition(null);
        persistencePort.save(itinerary);

        // Remove from Redis queue to update queue positions for remaining items
        queueManagementPort.remove(itineraryId);
        queueManagementPort.markProcessingCompleted(itineraryId);

        // Publish domain event
        eventPublisher.publish(new ItineraryCompletedEvent(
                itinerary.getId(),
                itinerary.getTitle(),
                itinerary.getTotalDistanceKm(),
                itinerary.getTotalDurationMinutes(),
                itinerary.getSteps() != null ? itinerary.getSteps().size() : 0,
                UUID.randomUUID().toString(),
                Instant.now()
        ));

        log.info("MarkItineraryStatusService: marked itinerary id={} as COMPLETED and removed from queue", itineraryId);
    }

    @Override
    @Transactional
    public void markFailed(UUID itineraryId, String failedStep, String reason) throws Throwable {
        Itinerary itinerary = (Itinerary) persistencePort.findById(itineraryId)
                .orElseThrow(() -> new IllegalArgumentException("Itinerary not found: " + itineraryId));

        itinerary.setStatus(ItineraryStatus.FAILED);
        itinerary.setQueuePosition(null);
        persistencePort.save(itinerary);

        // Remove from Redis queue to update queue positions for remaining items
        queueManagementPort.remove(itineraryId);
        queueManagementPort.markProcessingCompleted(itineraryId);

        // Publish domain event
        eventPublisher.publish(new ItineraryFailedEvent(
                itinerary.getId(),
                itinerary.getTitle(),
                String.format("Failed at %s: %s", failedStep, reason),
                UUID.randomUUID().toString(),
                Instant.now()
        ));

        log.warn("MarkItineraryStatusService: marked itinerary id={} as FAILED and removed from queue (step={}, reason={})",
                 itineraryId, failedStep, reason);
    }
}
