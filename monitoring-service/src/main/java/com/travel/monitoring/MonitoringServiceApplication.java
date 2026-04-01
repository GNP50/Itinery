package com.travel.monitoring;

import com.travel.monitoring.config.MetricsConfig;
import com.travel.monitoring.strategy.ConsoleStrategy;
import com.travel.monitoring.strategy.DatadogStrategy;
import com.travel.monitoring.strategy.OpenTelemetryStrategy;
import com.travel.monitoring.strategy.TelemetryStrategy;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import java.util.Map;

/**
 * Main application class for the Monitoring Service.
 * Provides comprehensive telemetry, metrics, and health monitoring capabilities.
 */
@SpringBootApplication
@ComponentScan(
    basePackages = "com.travel.monitoring",
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
            ConsoleStrategy.class,
            DatadogStrategy.class,
            OpenTelemetryStrategy.class
        })
    }
)
public class MonitoringServiceApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(MonitoringServiceApplication.class, args);

        // Register shutdown hook for graceful shutdown
        context.registerShutdownHook();

        System.out.println("========================================");
        System.out.println("Monitoring Service started successfully!");
        System.out.println("========================================");
    }

    /**
     * Creates a OpenTelemetry bean for telemetry operations.
     */
    @Bean
    public OpenTelemetry openTelemetry() {
        return OpenTelemetry.noop();
    }

}
