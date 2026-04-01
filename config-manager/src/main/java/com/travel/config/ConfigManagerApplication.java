package com.travel.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Config Manager microservice.
 * <p>
 * Exposes a centralised configuration store via REST ({@code /api/v1/config})
 * and gRPC (port 9091), backed by PostgreSQL with Flyway-managed migrations.
 */
@SpringBootApplication
public class ConfigManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigManagerApplication.class, args);
    }
}
