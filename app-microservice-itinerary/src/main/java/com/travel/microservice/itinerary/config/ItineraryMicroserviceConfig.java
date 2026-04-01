package com.travel.microservice.itinerary.config;

import org.springframework.context.annotation.Configuration;

/**
 * Microservice-level configuration.
 * All outbound adapters (persistence, cache, queue, geo, AI) are registered
 * automatically via @Component scanning in itinerary-impl.
 */
@Configuration
public class ItineraryMicroserviceConfig {
}
