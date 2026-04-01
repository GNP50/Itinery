package com.travel.microservice.itinerary;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the microservice deployment.
 * This service owns the itinerary domain and its own database.
 */
@SpringBootApplication(scanBasePackages = "com.travel")
@EntityScan(basePackages = "com.travel")
@EnableJpaRepositories(basePackages = "com.travel")
@EnableScheduling
public class ItineraryMicroserviceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ItineraryMicroserviceApplication.class, args);
        System.out.println("========================================");
        System.out.println("Itinerary Microservice");
        System.out.println("========================================");
        System.out.println("Server running on: http://localhost:8085");
        System.out.println("Swagger UI: http://localhost:8085/swagger-ui.html");
        System.out.println("Actuator: http://localhost:8081/actuator");
        System.out.println("========================================");
    }
}
