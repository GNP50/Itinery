package com.travel.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.Optional;

/**
 * Spring Boot {@link AutoConfiguration} for the Transactional Outbox pattern.
 *
 * <h3>What this configures</h3>
 * <p>The consuming application must declare {@code @EnableJpaRepositories} covering
 * the {@code com.travel.messaging} package (or a parent package) so that
 * {@link OutboxRepository} is registered.  {@link org.springframework.boot.autoconfigure.AutoConfigurationPackage}
 * on this class registers the package for entity scanning when no explicit
 * {@code @EntityScan} is present.
 * <ul>
 *   <li>{@link OutboxRepository} – Spring Data JPA repository</li>
 *   <li>{@link OutboxEventPublisher} – {@link EventPublisherPort} implementation</li>
 *   <li>{@link OutboxPublisher} – scheduled Kafka relay</li>
 *   <li>{@link CorrelationIdFilter} – servlet filter for X-Correlation-Id</li>
 *   <li>Micrometer {@link Gauge} for outbox lag (when Micrometer is on the classpath)</li>
 * </ul>
 *
 * <h3>Activation</h3>
 * <p>Enabled by default.  To disable set:
 * <pre>
 * outbox:
 *   enabled: false
 * </pre>
 *
 * <h3>Scheduling</h3>
 * <p>{@link EnableScheduling} is activated here so that consumers of this
 * starter do not need to add it themselves.  If the consuming application
 * already has {@code @EnableScheduling} the annotation is idempotent.
 */
@AutoConfiguration
@AutoConfigurationPackage
@ConditionalOnProperty(
        prefix = "outbox",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@ConditionalOnClass({ KafkaTemplate.class, OutboxRepository.class })
@EnableScheduling
@EnableTransactionManagement
public class OutboxAutoConfiguration {

    // ── ObjectMapper ──────────────────────────────────────────────────────────

    /**
     * Registers a Jackson {@link ObjectMapper} configured for the outbox
     * serialiser if no {@code ObjectMapper} bean is already present.
     *
     * <p>Applications that declare their own {@code ObjectMapper} bean (e.g.
     * via Spring Boot's {@code JacksonAutoConfiguration}) will use that bean
     * instead – this one serves as a safe fallback.
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper outboxObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    // ── Core outbox beans ─────────────────────────────────────────────────────

    /**
     * Registers the {@link OutboxEventPublisher} as the default
     * {@link EventPublisherPort} adapter.
     */
    @Bean
    @ConditionalOnMissingBean(EventPublisherPort.class)
    public OutboxEventPublisher outboxEventPublisher(
            OutboxRepository outboxRepository,
            ObjectMapper objectMapper) {
        return new OutboxEventPublisher(outboxRepository, objectMapper);
    }

    /**
     * Registers the scheduled {@link OutboxPublisher} that relays outbox rows
     * to Kafka.
     */
    @Bean
    @ConditionalOnMissingBean
    public OutboxPublisher outboxPublisher(
            OutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            Optional<MeterRegistry> meterRegistry) {
        return new OutboxPublisher(outboxRepository, kafkaTemplate, meterRegistry);
    }

    // ── Servlet filter ────────────────────────────────────────────────────────

    /**
     * Registers the {@link CorrelationIdFilter}.
     *
     * <p>Conditional on the Servlet API being present so the starter remains
     * usable in non-web (e.g. batch) contexts.
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "jakarta.servlet.Filter")
    public CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }

    // ── Micrometer integration ────────────────────────────────────────────────

    /**
     * Registers a Micrometer {@link Gauge} that exposes the number of unpublished
     * outbox events.  This metric can be used to alert on outbox relay failures
     * (e.g. when the gauge remains non-zero for an extended period).
     *
     * <p>Only registered when a {@link MeterRegistry} bean is available.
     */
    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnMissingBean(name = "outboxLagGauge")
    public Gauge outboxLagGauge(OutboxRepository outboxRepository,
                                MeterRegistry meterRegistry) {
        return Gauge.builder("outbox.events.pending",
                        outboxRepository,
                        repo -> (double) repo.countByPublishedFalse())
                .description("Number of outbox events awaiting publication to Kafka")
                .register(meterRegistry);
    }
}
