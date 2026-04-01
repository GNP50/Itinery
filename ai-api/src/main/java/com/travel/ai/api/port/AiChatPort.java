package com.travel.ai.api.port;

import com.travel.ai.api.dto.AiChatDTO;

/**
 * Outbound SPI: AI conversational chat grounded in a specific itinerary context.
 * <p>
 * Implementations may call OpenAI chat completions, Anthropic Messages API, or
 * any compatible conversational AI service. The {@code itineraryContext} parameter
 * allows the caller to inject a pre-computed, structured summary of the itinerary
 * so that the AI can answer questions about it without additional data fetching.
 */
public interface AiChatPort {

    /**
     * Process a single conversational turn.
     *
     * @param request          the chat request including message and conversation
     *                         history; must not be {@code null}
     * @param itineraryContext serialised context string representing the itinerary
     *                         (e.g. a JSON summary or a plain-text description);
     *                         may be {@code null} when no itinerary context is needed
     * @return the AI assistant's reply with optional follow-up suggestions;
     *         never {@code null}
     * @throws AiChatException if the AI service is unavailable or returns an
     *                         unusable response
     */
    AiChatDTO.Response chat(AiChatDTO.Request request, String itineraryContext);

    /**
     * Unchecked exception thrown when an AI chat request cannot be completed.
     */
    class AiChatException extends RuntimeException {

        public AiChatException(String message) {
            super(message);
        }

        public AiChatException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
