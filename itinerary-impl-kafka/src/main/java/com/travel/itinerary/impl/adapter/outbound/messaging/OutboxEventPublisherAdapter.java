package com.travel.itinerary.impl.adapter.outbound.messaging;

import com.travel.itinerary.api.port.outbound.EventPublisherPort;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

/**
 * Adapter that bridges the itinerary domain's {@link EventPublisherPort}
 * (single-arg {@code publish(Object)}) to the messaging starter's
 * {@link com.travel.messaging.EventPublisherPort}
 * (four-arg {@code publish(String, UUID, String, Object)}).
 *
 * <p>Routing: the {@code aggregateType} is set to the target Kafka topic name
 * so that {@link com.travel.messaging.OutboxPublisher} publishes each event to
 * the correct topic (it uses {@code aggregateType.toLowerCase()} as the topic).
 *
 * <ul>
 *   <li>{@code ItineraryCreatedEvent}   → {@code itinerary.created}</li>
 *   <li>{@code ItineraryUpdatedEvent}   → {@code itinerary.updated}</li>
 *   <li>{@code ItineraryCompletedEvent} → {@code itinerary.completed}</li>
 *   <li>{@code ItineraryFailedEvent}    → {@code itinerary.failed}</li>
 *   <li>{@code StepProcessedEvent}      → {@code itinerary.step.processed}</li>
 *   <li>Other events                    → {@code itinerary} (legacy fallback)</li>
 * </ul>
 */
@Component
public class OutboxEventPublisherAdapter implements EventPublisherPort {

    private static final Map<String, String> TOPIC_MAP = Map.of(
            "ItineraryCreatedEvent",   "itinerary.created",
            "ItineraryUpdatedEvent",   "itinerary.updated",
            "ItineraryCompletedEvent", "itinerary.completed",
            "ItineraryFailedEvent",    "itinerary.failed",
            "StepProcessedEvent",      "itinerary.step.processed"
    );

    private final com.travel.messaging.EventPublisherPort delegate;

    public OutboxEventPublisherAdapter(com.travel.messaging.EventPublisherPort delegate) {
        this.delegate = delegate;
    }

    @Override
    public void publish(Object event) {
        String eventType     = event.getClass().getSimpleName();
        UUID   aggregateId   = extractAggregateId(event);
        // Use the exact Kafka topic as aggregateType so OutboxPublisher routes correctly.
        String aggregateType = TOPIC_MAP.getOrDefault(eventType, "itinerary");

        delegate.publish(aggregateType, aggregateId, eventType, event);
    }

    /**
     * Tries to extract a UUID aggregate identifier from the event by calling
     * {@code id()} (records with an {@code id} component) or
     * {@code itineraryId()} (events that carry the parent itinerary reference).
     * Falls back to a random UUID so that publication never blocks the business
     * operation.
     */
    private UUID extractAggregateId(Object event) {
        for (String methodName : new String[]{"id", "itineraryId"}) {
            try {
                Method m = event.getClass().getMethod(methodName);
                Object value = m.invoke(event);
                if (value instanceof UUID uuid) {
                    return uuid;
                }
            } catch (Exception ignored) {
                // method not present or wrong return type – try next
            }
        }
        return UUID.randomUUID();
    }
}
