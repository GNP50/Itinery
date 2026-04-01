package com.travel.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scheduled outbox relay that polls the {@link OutboxRepository} for
 * unpublished events and forwards them to Kafka using a {@link KafkaTemplate}.
 *
 * <h3>Execution model</h3>
 * <ul>
 *   <li>Runs every 500 ms (fixed-delay) in a single thread so that publication
 *       order is preserved within a poll batch.</li>
 *   <li>Each batch is wrapped in a {@link Transactional} boundary: if a Kafka
 *       send fails the transaction is rolled back and the rows remain
 *       {@code published=false} so they will be retried in the next cycle.</li>
 *   <li>When a send succeeds the row's {@code published} flag and
 *       {@code publishedAt} timestamp are updated in the same transaction.</li>
 * </ul>
 *
 * <h3>Topic naming convention</h3>
 * <p>Topics are derived from the {@code aggregateType} field:
 * {@code aggregateType.toLowerCase()} → e.g. {@code "itinerary"} maps to the
 * Kafka topic {@code "itinerary"}.  Override by subclassing and providing your
 * own {@link #resolveTopic(OutboxEvent)} implementation.
 *
 * <h3>Kafka headers forwarded</h3>
 * <table>
 *   <tr><th>Header</th><th>Source</th></tr>
 *   <tr><td>{@code X-Event-Type}</td><td>{@link OutboxEvent#getEventType()}</td></tr>
 *   <tr><td>{@code X-Aggregate-Type}</td><td>{@link OutboxEvent#getAggregateType()}</td></tr>
 *   <tr><td>{@code X-Aggregate-Id}</td><td>{@link OutboxEvent#getAggregateId()}</td></tr>
 *   <tr><td>{@code X-Correlation-Id}</td><td>{@link OutboxEvent#getCorrelationId()}</td></tr>
 *   <tr><td>{@code X-Outbox-Event-Id}</td><td>{@link OutboxEvent#getId()}</td></tr>
 * </table>
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private static final String HEADER_EVENT_TYPE      = "X-Event-Type";
    private static final String HEADER_AGGREGATE_TYPE  = "X-Aggregate-Type";
    private static final String HEADER_AGGREGATE_ID    = "X-Aggregate-Id";
    private static final String HEADER_CORRELATION_ID  = "X-Correlation-Id";
    private static final String HEADER_OUTBOX_EVENT_ID = "X-Outbox-Event-Id";

    // ── Dependencies ─────────────────────────────────────────────────────────

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Optional<MeterRegistry> meterRegistry;

    // ── Metrics ───────────────────────────────────────────────────────────────

    private Counter publishedCounter;
    private Counter failedCounter;
    private Timer   publishTimer;

    // ── Constructor ──────────────────────────────────────────────────────────

    public OutboxPublisher(OutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           Optional<MeterRegistry> meterRegistry) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate    = kafkaTemplate;
        this.meterRegistry    = meterRegistry;

        meterRegistry.ifPresent(registry -> {
            publishedCounter = Counter.builder("outbox.events.published")
                    .description("Number of outbox events successfully published to Kafka")
                    .register(registry);
            failedCounter = Counter.builder("outbox.events.failed")
                    .description("Number of outbox events that failed to publish")
                    .register(registry);
            publishTimer = Timer.builder("outbox.publish.duration")
                    .description("Time taken to process one outbox poll batch")
                    .register(registry);
        });
    }

    // ── Scheduled relay ──────────────────────────────────────────────────────

    /**
     * Polls the outbox table and relays pending events to Kafka.
     *
     * <p>The {@code fixedDelay} semantics mean the next execution starts
     * 500 ms <em>after the previous one completes</em>, preventing overlapping
     * executions under sustained load.
     */
    @Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay-ms:500}")
    @Transactional
    public void relayPendingEvents() {
        long startNs = System.nanoTime();
        AtomicInteger published = new AtomicInteger(0);
        AtomicInteger failed    = new AtomicInteger(0);

        try {
            List<OutboxEvent> pending =
                    outboxRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc();

            if (pending.isEmpty()) {
                return;
            }

            log.debug("Outbox poller: found {} unpublished event(s)", pending.size());

            for (OutboxEvent event : pending) {
                try {
                    sendToKafka(event);
                    markPublished(event);
                    published.incrementAndGet();
                } catch (Exception ex) {
                    failed.incrementAndGet();
                    log.error("Failed to publish outbox event id={} type={}: {}",
                            event.getId(), event.getEventType(), ex.getMessage(), ex);
                    // Rethrow to trigger transaction rollback so the event
                    // remains unpublished and will be retried.
                    throw ex;
                }
            }

        } finally {
            long durationNs = System.nanoTime() - startNs;
            int pub = published.get();
            int fail = failed.get();

            if (pub > 0 || fail > 0) {
                log.info("Outbox relay batch complete: published={}, failed={}, durationMs={}",
                        pub, fail, durationNs / 1_000_000);
            }

            meterRegistry.ifPresent(r -> {
                if (publishedCounter != null) publishedCounter.increment(pub);
                if (failedCounter    != null) failedCounter.increment(fail);
                if (publishTimer     != null) publishTimer.record(
                        durationNs, java.util.concurrent.TimeUnit.NANOSECONDS);
            });
        }
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Constructs a {@link ProducerRecord} with event-metadata headers and
     * sends it synchronously via the {@link KafkaTemplate}.
     *
     * <p>The aggregate ID is used as the Kafka message key so that all events
     * for the same aggregate land on the same partition (preserving order).
     */
    private void sendToKafka(OutboxEvent event) {
        String topic = resolveTopic(event);
        String key   = event.getAggregateId().toString();

        ProducerRecord<String, String> record =
                new ProducerRecord<>(topic, null, key, event.getPayload());

        // Attach metadata headers for consumer-side routing and tracing.
        addHeader(record, HEADER_EVENT_TYPE,      event.getEventType());
        addHeader(record, HEADER_AGGREGATE_TYPE,  event.getAggregateType());
        addHeader(record, HEADER_AGGREGATE_ID,    event.getAggregateId().toString());
        addHeader(record, HEADER_OUTBOX_EVENT_ID, event.getId().toString());
        if (event.getCorrelationId() != null) {
            addHeader(record, HEADER_CORRELATION_ID, event.getCorrelationId());
        }

        // Block until the broker acknowledges the send so that we only mark
        // the event as published after a durable write.
        SendResult<String, String> result = kafkaTemplate.send(record).join();
        log.debug("Published event id={} to {}@{}", event.getId(), topic,
                result.getRecordMetadata().offset());
    }

    /**
     * Marks an outbox event as published by setting {@code published=true}
     * and recording the publication timestamp.  Called within the same
     * transaction as {@link #sendToKafka} so both actions are atomic.
     */
    private void markPublished(OutboxEvent event) {
        event.setPublished(true);
        event.setPublishedAt(Instant.now());
        outboxRepository.save(event);
    }

    /**
     * Derives the target Kafka topic from the event's {@code aggregateType}.
     *
     * <p>Default convention: {@code aggregateType} is lower-cased and used
     * directly as the topic name.  Override this method in a subclass to
     * implement custom routing (e.g. prefixing with environment names or
     * domain namespaces).
     *
     * @param event the outbox event being relayed
     * @return Kafka topic name, never {@code null} or blank
     */
    protected String resolveTopic(OutboxEvent event) {
        return event.getAggregateType().toLowerCase();
    }

    /** Convenience method to add a UTF-8 string header to a {@link ProducerRecord}. */
    private static void addHeader(ProducerRecord<String, String> record,
                                  String name, String value) {
        if (value != null) {
            record.headers().add(new RecordHeader(name,
                    value.getBytes(StandardCharsets.UTF_8)));
        }
    }
}
