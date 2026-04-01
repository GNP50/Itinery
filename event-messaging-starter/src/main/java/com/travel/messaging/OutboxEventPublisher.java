package com.travel.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Default implementation of {@link EventPublisherPort} that persists domain
 * events to the {@code outbox_events} table inside the caller's transaction.
 *
 * <h3>Transactional contract</h3>
 * <p>The method runs with {@link Propagation#MANDATORY}, meaning it MUST be
 * called from within an active transaction.  This enforces the Transactional
 * Outbox guarantee: the outbox row is written atomically with the business
 * state change.  If no transaction is active Spring will throw an
 * {@link org.springframework.transaction.IllegalTransactionStateException}
 * at runtime, alerting the developer to a missing {@code @Transactional}
 * annotation on the calling service.
 *
 * <h3>Correlation ID</h3>
 * <p>If {@link CorrelationIdFilter} has placed a correlation ID in the SLF4J
 * {@link MDC} under the key {@code "correlationId"} it is copied to the outbox
 * row and later forwarded to Kafka as the {@code X-Correlation-Id} header.
 */
@Component
public class OutboxEventPublisher implements EventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);

    /** MDC key set by {@link CorrelationIdFilter}. */
    static final String MDC_CORRELATION_ID = "correlationId";

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisher(OutboxRepository outboxRepository,
                                ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper     = objectMapper;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Serialises {@code payload} to JSON using Jackson and saves an
     * {@link OutboxEvent} row.  The correlation ID is read from the SLF4J MDC
     * if available.
     *
     * @throws EventPublicationException if {@code payload} cannot be serialised
     *                                   or the repository save fails
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String aggregateType,
                        UUID   aggregateId,
                        String eventType,
                        Object payload) {

        if (aggregateType == null || aggregateType.isBlank()) {
            throw new IllegalArgumentException("aggregateType must not be null or blank");
        }
        if (aggregateId == null) {
            throw new IllegalArgumentException("aggregateId must not be null");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be null or blank");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new EventPublicationException(
                    "Failed to serialise event payload for type '" + eventType + "'", ex);
        }

        OutboxEvent event = new OutboxEvent();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payloadJson);
        event.setCorrelationId(MDC.get(MDC_CORRELATION_ID));
        // published defaults to false; createdAt is set by @PrePersist

        try {
            outboxRepository.save(event);
        } catch (Exception ex) {
            throw new EventPublicationException(
                    "Failed to persist outbox event for aggregate " + aggregateId, ex);
        }

        log.debug("Outbox event saved: id={} type={} aggregate={}:{}",
                event.getId(), eventType, aggregateType, aggregateId);
    }
}
