package com.travel.messaging;

import java.util.UUID;

/**
 * Primary port (outbound) for publishing domain events.
 *
 * <p>Domain code depends on this interface – not on any Kafka or JPA
 * implementation detail.  The default adapter is {@link OutboxEventPublisher},
 * which persists events to the {@code outbox_events} table inside the
 * current database transaction so that publication is atomic with the
 * business state change (Transactional Outbox pattern).
 *
 * <h3>Usage example</h3>
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * public class ItineraryService {
 *
 *     private final EventPublisherPort eventPublisher;
 *
 *     @Transactional
 *     public void createItinerary(CreateItineraryCommand cmd) {
 *         Itinerary itinerary = ... // business logic
 *         itineraryRepository.save(itinerary);
 *
 *         eventPublisher.publish(
 *             "Itinerary",
 *             itinerary.getId(),
 *             "ItineraryCreatedEvent",
 *             new ItineraryCreatedEvent(itinerary.getId(), cmd.getOwnerId())
 *         );
 *     }
 * }
 * }</pre>
 *
 * <p><strong>Contract:</strong> implementations MUST persist the event in the
 * same transaction as the caller to guarantee at-least-once delivery.
 */
public interface EventPublisherPort {

    /**
     * Publishes a domain event.
     *
     * <p>The method must be called within an active transaction.  Implementations
     * should write the event to a durable store (e.g. outbox table) so that it
     * survives a process crash between commit and Kafka send.
     *
     * @param aggregateType  logical type of the aggregate (e.g. {@code "Itinerary"});
     *                       used to derive the Kafka topic name – must not be
     *                       {@code null} or blank
     * @param aggregateId    identity of the aggregate instance producing the event;
     *                       used as the Kafka partition key to preserve per-aggregate
     *                       ordering – must not be {@code null}
     * @param eventType      fully-qualified or short event type name
     *                       (e.g. {@code "ItineraryCreatedEvent"}) forwarded as a
     *                       Kafka header – must not be {@code null} or blank
     * @param payload        the event data object; serialised to JSON by the
     *                       implementation before storage – must not be {@code null}
     * @throws EventPublicationException if the event cannot be persisted (e.g.
     *                                   serialisation failure or DB constraint violation)
     */
    void publish(String aggregateType, UUID aggregateId, String eventType, Object payload);
}
