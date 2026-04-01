package com.travel.monolith;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the monolithic deployment.
 * This application includes all modules in a single JAR.
 */
@SpringBootApplication(scanBasePackages = "com.travel")
@EntityScan(basePackages = "com.travel")
@EnableJpaRepositories(basePackages = "com.travel")
@EnableScheduling
public class MonolithApplication {

    public static void main(String[] args) {
        SpringApplication.run(MonolithApplication.class, args);
        System.out.println("========================================");
        System.out.println("Travel Itinerary Platform - Monolith");
        System.out.println("========================================");
        System.out.println("Server running on: http://localhost:8080");
        System.out.println("Swagger UI: http://localhost:8080/swagger-ui.html");
        System.out.println("Actuator: http://localhost:8081/actuator");
        System.out.println("========================================");
    }
}
