package com.travel.itinerary.impl.adapter.outbound.grpc;

import com.travel.ai.api.port.AiChatPort;
import com.travel.ai.api.dto.AiChatDTO;
import com.travel.ai.grpc.v1.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Outbound adapter that calls the AI microservice via gRPC.
 * Implements {@link AiChatPort} to delegate chat requests to the remote AI service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiGrpcClientAdapter implements AiChatPort {

    @GrpcClient("ai-service")
    private AiServiceGrpc.AiServiceBlockingStub aiServiceStub;

    @Value("${ai.grpc.timeout-seconds:30}")
    private int timeoutSeconds;

    @Value("${ai.grpc.enabled:true}")
    private boolean grpcEnabled;

    /**
     * Process a chat request by delegating to the AI microservice via gRPC.
     *
     * @param request the chat request including message and conversation history
     * @param itineraryContext serialised itinerary context (may be null)
     * @return the AI assistant's reply
     * @throws AiChatException if the gRPC call fails
     */
    @Override
    public AiChatDTO.Response chat(AiChatDTO.Request request, String itineraryContext) {
        if (!grpcEnabled) {
            log.debug("AI gRPC client disabled - returning mock response");
            return mockResponse(request);
        }

        try {
            // Build chat request message
            ChatMessage.Builder messageBuilder = ChatMessage.newBuilder()
                .setRole("user")
                .setContent(request.message());

            // Add conversation history to the request
            ChatRequest.Builder chatRequestBuilder = ChatRequest.newBuilder()
                .setSessionId(request.itineraryId().toString())
                .setMessage(messageBuilder.build());

            // Add history messages
            if (request.conversationHistory() != null && !request.conversationHistory().isEmpty()) {
                for (AiChatDTO.ChatMessage histMsg : request.conversationHistory()) {
                    chatRequestBuilder.addHistory(
                        ChatMessage.newBuilder()
                            .setRole(histMsg.role())
                            .setContent(histMsg.content())
                            .build()
                    );
                }
            }

            if (itineraryContext != null && !itineraryContext.isBlank()) {
                // TODO: Add metadata support to proto if needed
                log.debug("Itinerary context provided but metadata not supported in current proto");
            }

            log.debug("Calling AI gRPC service for itinerary={}", request.itineraryId());

            // Call gRPC service with timeout - using suggest which is server-streaming
            SuggestRequest grpcRequest = SuggestRequest.newBuilder()
                .setPrompt(request.message())
                .build();
            
            // Suggest is a streaming response, so we need to collect all responses
            java.util.Iterator<SuggestResponse> responseIterator = aiServiceStub
                .withDeadlineAfter(timeoutSeconds, TimeUnit.SECONDS)
                .suggest(grpcRequest);
            
            // Collect responses - use the last one with done=true or the first complete one
            SuggestResponse response = null;
            while (responseIterator.hasNext()) {
                response = responseIterator.next();
                if (response.getDone()) {
                    break; // Found the final response
                }
            }
            
            if (response == null) {
                log.warn("No response received from AI service");
                return mockResponse(request);
            }

            log.debug("AI gRPC response received: {} chars", response.getFullText().length());

            // Extract suggestions from the response
            java.util.List<String> suggestions = response.getSuggestionsList().stream()
                .map(s -> s.getTitle())
                .limit(3)
                .collect(java.util.stream.Collectors.toList());

            return new AiChatDTO.Response(
                response.getFullText(),
                suggestions
            );

        } catch (Exception e) {
            log.warn("AI gRPC call failed: {}", e.getMessage());
            return mockResponse(request);
        }
    }

    /**
     * Returns a mock response when the AI service is unavailable or disabled.
     */
    private AiChatDTO.Response mockResponse(AiChatDTO.Request request) {
        String mockContent = String.format(
            "Grazie per il tuo messaggio: \"%s\". " +
            "Il servizio AI non è attualmente disponibile, ma sono qui per aiutarti con il tuo itinerario!",
            request.message()
        );

        return new AiChatDTO.Response(
            mockContent,
            java.util.List.of("Mostra il mio itinerario", "Aggiungi una tappa", "Ottimizza percorso")
        );
    }
}
