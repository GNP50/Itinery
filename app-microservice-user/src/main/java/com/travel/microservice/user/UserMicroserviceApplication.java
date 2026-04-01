package com.travel.microservice.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * User microservice entry point.
 * <p>
 * Exposes a gRPC server for user lookups and admin-status checks.
 * Shares the PostgreSQL {@code itinerary_db} with the monolith.
 * Schema management (Flyway) is performed by {@code app-monolith}.
 */
@SpringBootApplication(scanBasePackages = {"com.travel.user", "com.travel.microservice.user"})
@EntityScan(basePackages = "com.travel.user.impl.domain")
@EnableJpaRepositories(basePackages = "com.travel.user.impl.adapter.outbound.persistence")
public class UserMicroserviceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserMicroserviceApplication.class, args);
    }
}
