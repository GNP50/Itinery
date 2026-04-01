package com.travel.itinerary.api.port.outbound;

/**
 * Outbound SPI: asynchronous domain-event publishing.
 * <p>
 * This interface re-exports a single {@code publish} method so that domain
 * use-cases can emit events without any direct dependency on a specific
 * messaging infrastructure (Kafka, RabbitMQ, Spring ApplicationEvent, etc.).
 * <p>
 * Implementations provided by the {@code event-messaging-starter} module
 * handle serialisation, topic routing and delivery guarantees transparently.
 */
public interface EventPublisherPort {

    /**
     * Publish a domain event to the configured message broker or event bus.
     * <p>
     * The method is fire-and-forget from the caller's perspective; delivery
     * guarantees (at-least-once, exactly-once) are determined by the
     * implementation.
     *
     * @param event the domain event to publish; must not be {@code null};
     *              the concrete type determines the target topic/exchange
     */
    void publish(Object event);
}
