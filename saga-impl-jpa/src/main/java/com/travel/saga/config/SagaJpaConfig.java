package com.travel.saga.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA configuration for Saga domain.
 * Enables JPA repositories and entity scanning.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.travel.saga.repository")
@EnableJpaAuditing
@EntityScan(basePackages = "com.travel.saga.domain")
public class SagaJpaConfig {
}
