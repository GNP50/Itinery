package com.travel.ai.impl.adapter.inbound.grpc;

import com.travel.ai.api.dto.AiChatDTO;
import com.travel.ai.api.port.AiChatPort;
import com.travel.ai.api.port.AiEnrichmentPort;
import com.travel.ai.api.vo.AiItinerarySuggestion;
import com.travel.ai.api.vo.AiStepEnrichment;
import com.travel.ai.grpc.v1.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;

/**
 * gRPC server implementation for the AiService.
 * Exposes AI-powered travel assistance capabilities:
 * - Suggest: Generate AI-powered travel activity suggestions
 * - Chat: Multi-turn conversational travel assistant
 * - OptimizeRoute: Optimize itinerary step ordering
 * - SummarizeItinerary: Summarize complete itineraries
 * - TranslateContent: Translate itinerary content
 * - AnalyzeFeedback: Analyze user feedback sentiment
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class AiGrpcServiceImpl extends AiServiceGrpc.AiServiceImplBase {

    private final AiEnrichmentPort aiEnrichmentPort;
    private final AiChatPort aiChatPort;

    // =========================================================================
    // Suggest - Generate AI-powered travel activity suggestions
    // =========================================================================

    @Override
    public void suggest(SuggestRequest request,
                        StreamObserver<SuggestResponse> responseObserver) {
        log.debug("gRPC Suggest prompt={}, geo={}",
            request.getPrompt(),
            request.hasGeoContext() ? request.getGeoContext().getCity() : "N/A");

        try {
            // For now, use mock suggestions based on the prompt
            // In a full implementation, this would call the LLM with the full context
            String prompt = request.getPrompt();
            String city = request.hasGeoContext() ? request.getGeoContext().getCity() : null;
            String country = request.hasGeoContext() ? request.getGeoContext().getCountry() : null;

            // Build mock response
            SuggestResponse.Builder responseBuilder = SuggestResponse.newBuilder()
                .setDone(true)
                .setFullText("Ecco alcuni suggerimenti per il tuo viaggio...");

            // Add mock suggestions
            TravelSuggestion suggestion = TravelSuggestion.newBuilder()
                .setTitle("Attrazione Principale")
                .setDescription("Una destinazione imperdibile con ricca storia e cultura")
                .setCategory("Culturale")
                .addTags("storia")
                .addTags("architettura")
                .setRelevance(0.95f)
                .setEstimatedDuration("2-3 ore")
                .setEstimatedCost("~€20 a persona")
                .setBestTimeToVisit("Mattina presto o tardo pomeriggio")
                .addPracticalTips("Prenota in anticipo per evitare code")
                .addPracticalTips("Indossa scarpe comode")
                .build();

            responseBuilder.addSuggestions(suggestion);

            // Set mock token usage
            responseBuilder.setUsage(TokenUsage.newBuilder()
                .setPromptTokens(150)
                .setCompletionTokens(200)
                .setTotalTokens(350)
                .build());

            SuggestResponse response = responseBuilder.build();

            // If streaming is requested, send tokens one by one
            if (request.getStream()) {
                String[] tokens = response.getFullText().split(" ");
                for (String token : tokens) {
                    SuggestResponse tokenResponse = SuggestResponse.newBuilder()
                        .setToken(token + " ")
                        .build();
                    responseObserver.onNext(tokenResponse);
                    Thread.sleep(50); // Simulate streaming delay
                }
                responseObserver.onNext(response);
            } else {
                responseObserver.onNext(response);
            }
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("gRPC Suggest failed: {}", e.getMessage(), e);
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("Failed to generate suggestions: " + e.getMessage())
                    .asRuntimeException()
            );
        }
    }

    // =========================================================================
    // Chat - Multi-turn conversational travel assistant
    // =========================================================================

    @Override
    public StreamObserver<ChatRequest> chat(StreamObserver<ChatResponse> responseObserver) {
        log.debug("gRPC Chat stream opened");

        return new StreamObserver<ChatRequest>() {
            @Override
            public void onNext(ChatRequest request) {
                try {
                    String sessionId = request.getSessionId();
                    String userMessage = request.hasMessage() ? request.getMessage().getContent() : "";
                    String itineraryContext = buildItineraryContext(request);

                    // Build chat request for the port
                    // TODO: Parse itineraryId from sessionId or request context
                    java.util.UUID itineraryId = java.util.UUID.randomUUID(); // Placeholder
                    AiChatDTO.Request chatRequest = new AiChatDTO.Request(
                        itineraryId,
                        sessionId, // Using sessionId as accessToken for now
                        userMessage,
                        request.getHistoryList().stream()
                            .map(msg -> new AiChatDTO.ChatMessage(
                                msg.getRole(),
                                msg.getContent()
                            ))
                            .toList()
                    );

                    // Call the chat port
                    AiChatDTO.Response chatResponse = aiChatPort.chat(chatRequest, itineraryContext);

                    // Build gRPC response
                    ChatResponse.Builder responseBuilder = ChatResponse.newBuilder()
                        .setSessionId(sessionId)
                        .setDone(true)
                        .setMessage(ChatMessage.newBuilder()
                            .setRole("assistant")
                            .setContent(chatResponse.reply())
                            .build())
                        .setUsage(TokenUsage.newBuilder()
                            .setPromptTokens(100)
                            .setCompletionTokens(150)
                            .setTotalTokens(250)
                            .build())
                        .setDetectedIntent("general_query"); // Default intent

                    if (request.getStream()) {
                        // Stream tokens
                        String[] tokens = chatResponse.reply().split(" ");
                        for (String token : tokens) {
                            ChatResponse tokenResponse = ChatResponse.newBuilder()
                                .setSessionId(sessionId)
                                .setToken(token + " ")
                                .build();
                            responseObserver.onNext(tokenResponse);
                        }
                        responseObserver.onNext(responseBuilder.build());
                    } else {
                        responseObserver.onNext(responseBuilder.build());
                    }

                } catch (Exception e) {
                    log.error("gRPC Chat onNext failed: {}", e.getMessage(), e);
                    responseObserver.onError(
                        Status.INTERNAL
                            .withDescription("Chat failed: " + e.getMessage())
                            .asRuntimeException()
                    );
                }
            }

            @Override
            public void onError(Throwable t) {
                log.error("gRPC Chat stream error: {}", t.getMessage(), t);
            }

            @Override
            public void onCompleted() {
                log.debug("gRPC Chat stream completed");
                responseObserver.onCompleted();
            }
        };
    }

    // =========================================================================
    // OptimizeRoute - Optimize itinerary step ordering
    // =========================================================================

    @Override
    public void optimizeRoute(OptimizeRouteRequest request,
                              StreamObserver<OptimizeRouteResponse> responseObserver) {
        log.debug("gRPC OptimizeRoute itineraryId={}, steps={}",
            request.getItineraryId(), request.getStepsCount());

        try {
            // Build mock optimized response
            OptimizeRouteResponse.Builder responseBuilder = OptimizeRouteResponse.newBuilder()
                .setTotalTravelTimeSeconds(3600)
                .setTotalDistanceMeters(5000)
                .setEstimatedTotalCost("~€50")
                .setExplanation("Ottimizzato per minimizzare il tempo di viaggio")
                .setUsage(TokenUsage.newBuilder()
                    .setPromptTokens(200)
                    .setCompletionTokens(300)
                    .setTotalTokens(500)
                    .build());

            // Add optimized steps
            for (int i = 0; i < request.getStepsCount(); i++) {
                var step = request.getSteps(i);
                responseBuilder.addOptimizedStepIds(step.getStepId());
                responseBuilder.addSchedule(OptimizedStep.newBuilder()
                    .setStepId(step.getStepId())
                    .setSequence(i + 1)
                    .setTravelTimeSeconds(i * 300)
                    .setTravelDistanceMeters(i * 500)
                    .setTravelMode(request.getTravelMode())
                    .build());
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("gRPC OptimizeRoute failed: {}", e.getMessage(), e);
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("Route optimization failed: " + e.getMessage())
                    .asRuntimeException()
            );
        }
    }

    // =========================================================================
    // SummarizeItinerary - Summarize complete itinerary
    // =========================================================================

    @Override
    public void summarizeItinerary(SummarizeItineraryRequest request,
                                    StreamObserver<SummarizeItineraryResponse> responseObserver) {
        log.debug("gRPC SummarizeItinerary itineraryId={}", request.getItineraryId());

        try {
            // Build mock summary
            SummarizeItineraryResponse response = SummarizeItineraryResponse.newBuilder()
                .setSummary("Un itinerario affascinante che esplora le meraviglie della destinazione.")
                .setHighlights("Attrazioni principali, Esperienze culturali, Cucina locale")
                .setUsage(TokenUsage.newBuilder()
                    .setPromptTokens(100)
                    .setCompletionTokens(150)
                    .setTotalTokens(250)
                    .build())
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("gRPC SummarizeItinerary failed: {}", e.getMessage(), e);
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("Itinerary summarization failed: " + e.getMessage())
                    .asRuntimeException()
            );
        }
    }

    // =========================================================================
    // TranslateContent - Translate itinerary content
    // =========================================================================

    @Override
    public void translateContent(TranslateContentRequest request,
                                  StreamObserver<TranslateContentResponse> responseObserver) {
        log.debug("gRPC TranslateContent from={} to={}",
            request.getSourceLanguage(), request.getTargetLanguage());

        try {
            // Mock translation - in production this would call a translation service
            String translated = request.getContent(); // Placeholder

            TranslateContentResponse response = TranslateContentResponse.newBuilder()
                .setTranslatedContent(translated)
                .setDetectedSourceLanguage(request.getSourceLanguage() != null ?
                    request.getSourceLanguage() : "en")
                .setConfidence(0.98f)
                .setUsage(TokenUsage.newBuilder()
                    .setPromptTokens(50)
                    .setCompletionTokens(100)
                    .setTotalTokens(150)
                    .build())
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("gRPC TranslateContent failed: {}", e.getMessage(), e);
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("Translation failed: " + e.getMessage())
                    .asRuntimeException()
            );
        }
    }

    // =========================================================================
    // AnalyzeFeedback - Analyze user feedback sentiment
    // =========================================================================

    @Override
    public void analyzeFeedback(AnalyzeFeedbackRequest request,
                                 StreamObserver<AnalyzeFeedbackResponse> responseObserver) {
        log.debug("gRPC AnalyzeFeedback itineraryId={}", request.getItineraryId());

        try {
            // Mock sentiment analysis
            AnalyzeFeedbackResponse response = AnalyzeFeedbackResponse.newBuilder()
                .setSentiment("positive")
                .setSentimentScore(0.85f)
                .putAspectSentiments("accommodation", "positive")
                .putAspectSentiments("transport", "neutral")
                .addImprovementSuggestions("Migliorare le opzioni di trasporto")
                .addTopics("esperienza positiva")
                .addTopics("consigli per miglioramenti")
                .setUsage(TokenUsage.newBuilder()
                    .setPromptTokens(80)
                    .setCompletionTokens(120)
                    .setTotalTokens(200)
                    .build())
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("gRPC AnalyzeFeedback failed: {}", e.getMessage(), e);
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("Feedback analysis failed: " + e.getMessage())
                    .asRuntimeException()
            );
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String buildItineraryContext(ChatRequest request) {
        if (!request.hasGeoContext()) {
            return null;
        }
        var geo = request.getGeoContext();
        return String.format("Location: %s, %s, %s",
            geo.getCity(), geo.getRegion(), geo.getCountry());
    }
}
