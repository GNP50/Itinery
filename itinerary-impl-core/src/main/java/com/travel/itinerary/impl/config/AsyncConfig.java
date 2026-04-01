package com.travel.itinerary.impl.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Asynchronous execution configuration.
 * <p>
 * Defines the {@link ThreadPoolTaskExecutor} bean used by
 * {@link com.travel.itinerary.impl.service.QueueManager} to submit
 * {@link com.travel.itinerary.impl.service.ItineraryProcessor} tasks.
 * <p>
 * {@code @EnableScheduling} is also activated here so that the
 * {@code @Scheduled} poll method in {@code QueueManager} is registered.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    /**
     * Thread pool dedicated to itinerary enrichment processing.
     *
     * <ul>
     *   <li>{@code corePoolSize}   – number of threads always kept alive</li>
     *   <li>{@code maxPoolSize}    – upper bound on concurrent threads</li>
     *   <li>{@code queueCapacity}  – internal task queue before rejection</li>
     *   <li>{@code threadNamePrefix} – prefix for named threads (useful for logs and
     *       thread dumps)</li>
     * </ul>
     *
     * The {@link ThreadPoolExecutor.CallerRunsPolicy} rejection handler is set so
     * that if the queue and pool are both exhausted the submitting thread runs the
     * task itself rather than discarding it silently.
     *
     * @return configured executor bean
     */
    @Bean(name = "itineraryProcessorExecutor")
    public ThreadPoolTaskExecutor itineraryProcessorExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("itinerary-processor-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * Default async executor for Spring {@code @Async} methods not specifically
     * qualified with a bean name.  Delegates to the same pool as the itinerary
     * processor so that all async work shares a single, bounded thread pool.
     *
     * @return the itinerary processor executor as the default async executor
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        return itineraryProcessorExecutor();
    }
}
