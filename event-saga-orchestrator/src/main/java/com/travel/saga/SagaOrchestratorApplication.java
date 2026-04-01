package com.travel.saga;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Saga Orchestrator Application.
 *
 * JPA configuration is provided by saga-impl-jpa module (SagaJpaConfig).
 * State machine configuration is provided by saga-impl-core module (ItinerarySagaConfig).
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
@ComponentScan(basePackages = {
    "com.travel.saga",
    "com.travel.queue"  // Include queue implementations
})
public class SagaOrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SagaOrchestratorApplication.class, args);
    }
}
