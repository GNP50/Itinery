package com.travel.saga.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Infrastructure beans for the Saga Orchestrator application.
 */
@Configuration
public class SagaConfig {

    /**
     * Task scheduler used by {@link com.travel.saga.service.SagaOrchestrationService}
     * to schedule delayed retries with exponential back-off.
     */
    @Bean
    public TaskScheduler sagaTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("saga-retry-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }
}
