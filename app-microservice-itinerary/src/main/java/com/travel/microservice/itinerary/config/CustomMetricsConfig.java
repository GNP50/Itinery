package com.travel.microservice.itinerary.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Configuration
public class CustomMetricsConfig {

    private static final String PROCESSING_TIME_METRIC_NAME = "itinerary.processing.time";
    private static final String QUEUE_LENGTH_METRIC_NAME = "itinerary.queue.length";
    private static final String PROCESSED_COUNT_METRIC_NAME = "itinerary.processed.count";
    private static final String FAILED_COUNT_METRIC_NAME = "itinerary.failed.count";

    @Bean
    public MetricsExporter metricsExporter(MeterRegistry registry) {
        return new MetricsExporter(registry);
    }

    public static class MetricsExporter {

        private final MeterRegistry registry;
        private final ConcurrentMap<String, Timer> timers = new ConcurrentHashMap<>();
        private final AtomicLong queueLength = new AtomicLong(0);
        private final AtomicLong processedCount = new AtomicLong(0);
        private final AtomicLong failedCount = new AtomicLong(0);

        public MetricsExporter(MeterRegistry registry) {
            this.registry = registry;
            registerMetrics();
        }

        private void registerMetrics() {
            registry.gauge(QUEUE_LENGTH_METRIC_NAME, queueLength);
            registry.gauge(PROCESSED_COUNT_METRIC_NAME, processedCount);
            registry.gauge(FAILED_COUNT_METRIC_NAME, failedCount);
        }

        public Timer startTimer(String name) {
            return timers.computeIfAbsent(name, n ->
                Timer.builder(PROCESSING_TIME_METRIC_NAME + "." + n)
                    .description("Timer for " + n)
                    .register(registry)
            );
        }

        public void incrementQueueLength() {
            queueLength.incrementAndGet();
        }

        public void decrementQueueLength() {
            queueLength.decrementAndGet();
        }

        public void recordProcessed() {
            processedCount.incrementAndGet();
        }

        public void recordFailed() {
            failedCount.incrementAndGet();
        }
    }
}
