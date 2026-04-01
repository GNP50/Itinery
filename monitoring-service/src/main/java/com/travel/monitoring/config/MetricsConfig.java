package com.travel.monitoring.config;

import com.travel.monitoring.strategy.TelemetryStrategy;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.web.client.RestTemplate;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Configuration class for metrics and telemetry.
 * Sets up Prometheus registry and telemetry strategy beans.
 */
@Configuration
@EnableAspectJAutoProxy
@ComponentScan(basePackages = "com.travel.monitoring")
public class MetricsConfig {

    /**
     * Configures Prometheus registry customizer.
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
            .commonTags("application", "monitoring-service");
    }

    /**
     * Selects the telemetry strategy based on configuration.
     * Falls back to ConsoleStrategy if no strategy is specified.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "monitoring", name = "strategy", havingValue = "console", matchIfMissing = true)
    public TelemetryStrategy consoleTelemetryStrategy() {
        return new com.travel.monitoring.strategy.ConsoleStrategy();
    }

    /**
     * Datadog telemetry strategy bean.
     */
    @Bean
    @ConditionalOnProperty(prefix = "monitoring", name = "strategy", havingValue = "datadog")
    public TelemetryStrategy datadogTelemetryStrategy() {
        return new com.travel.monitoring.strategy.DatadogStrategy(
            new com.fasterxml.jackson.databind.ObjectMapper());
    }

    /**
     * OpenTelemetry telemetry strategy bean.
     */
    @Bean
    @ConditionalOnProperty(prefix = "monitoring", name = "strategy", havingValue = "opentelemetry")
    public TelemetryStrategy openTelemetryTelemetryStrategy() {
        return new com.travel.monitoring.strategy.OpenTelemetryStrategy(
            io.opentelemetry.api.OpenTelemetry.noop());
    }

    /**
     * RestTemplate bean for health checks and external API calls.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
