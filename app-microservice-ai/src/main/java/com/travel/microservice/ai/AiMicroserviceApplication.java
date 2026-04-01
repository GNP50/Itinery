package com.travel.microservice.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI Microservice entry point.
 * <p>
 * Exposes a gRPC server for AI-powered travel assistance:
 * - Suggest: Generate AI-powered travel activity suggestions
 * - Chat: Multi-turn conversational travel assistant
 * - OptimizeRoute: Optimize itinerary step ordering
 * - SummarizeItinerary: Summarize complete itineraries
 * - TranslateContent: Translate itinerary content
 * - AnalyzeFeedback: Analyze user feedback sentiment
 * <p>
 * Uses Ollama with llama3.1:8b model for LLM inference.
 */
@SpringBootApplication(scanBasePackages = "com.travel.ai")
public class AiMicroserviceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiMicroserviceApplication.class, args);
    }
}
