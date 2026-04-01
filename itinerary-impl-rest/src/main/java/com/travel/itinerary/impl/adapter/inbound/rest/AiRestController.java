package com.travel.itinerary.impl.adapter.inbound.rest;

import com.travel.ai.api.port.AiEnrichmentPort;
import com.travel.ai.api.vo.AiItinerarySuggestion;
import com.travel.ai.api.vo.AiStepEnrichment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST adapter providing direct access to AI enrichment capabilities.
 * <p>
 * All endpoints accept JSON and return JSON.  The {@code itinerary_id} and
 * {@code access_token} fields are included in the request bodies so that
 * callers can identify themselves without an additional auth layer on these
 * convenience endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI", description = "AI-powered itinerary enrichment and suggestions")
public class AiRestController {

    private final AiEnrichmentPort aiEnrichmentPort;

    // -------------------------------------------------------------------------
    // Nested request / response records (Java 21)
    // -------------------------------------------------------------------------

    /**
     * Request payload for the {@code /suggest} endpoint.
     *
     * @param itineraryId  UUID of the itinerary to associate with (optional)
     * @param accessToken  access token for the itinerary (optional)
     * @param placeName    canonical place name to enrich
     * @param city         city component of the address
     * @param region       region component of the address
     * @param country      full country name
     * @param travelMode   mode of transport
     * @param interests    list of traveller interest tags
     */
    public record SuggestRequest(
            UUID         itineraryId,
            String       accessToken,
            @NotBlank String       placeName,
            String       city,
            String       region,
            String       country,
            String       travelMode,
            List<String> interests
    ) {}

    /**
     * Request payload for the {@code /optimize-route} endpoint.
     *
     * @param itineraryId  UUID of the itinerary (optional)
     * @param accessToken  access token for the itinerary (optional)
     * @param steps        ordered list of place names to optimise
     */
    public record OptimizeRouteRequest(
            UUID         itineraryId,
            String       accessToken,
            List<String> steps
    ) {}

    /**
     * Request payload for the {@code /chat} endpoint.
     *
     * @param itineraryId         UUID of the itinerary context (optional)
     * @param accessToken         access token for the itinerary (optional)
     * @param message             user message to send to the AI
     * @param conversationHistory previous conversation turns for context
     */
    public record AiChatRequest(
            UUID         itineraryId,
            String       accessToken,
            @NotBlank String       message,
            List<String> conversationHistory
    ) {}

    /**
     * Response body for the {@code /chat} endpoint.
     *
     * @param reply               AI reply to the user message
     * @param updatedHistory      full conversation history including the new turn
     */
    public record AiChatResponse(
            String       reply,
            List<String> updatedHistory
    ) {}

    /**
     * Response body for the {@code /optimize-route} endpoint.
     *
     * @param optimizedOrder      step names in the AI-suggested optimal order
     * @param rationale           brief explanation of the suggested ordering
     */
    public record OptimizeRouteResponse(
            List<String> optimizedOrder,
            String       rationale
    ) {}

    // -------------------------------------------------------------------------
    // POST /suggest  – enrich a single step
    // -------------------------------------------------------------------------

    @Operation(
        summary     = "Suggest enrichment for a single place",
        description = "Generates an AI description and travel tips for the specified place, "
                    + "optionally personalised by travel mode and interests."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "AI enrichment generated"),
        @ApiResponse(responseCode = "400", description = "Invalid request",
                     content = @Content(schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "AI service unavailable",
                     content = @Content(schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class)))
    })
    @PostMapping(value = "/suggest",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AiStepEnrichment> suggest(
            @Valid @RequestBody SuggestRequest request) {

        log.debug("POST /api/v1/ai/suggest placeName='{}'", request.placeName());
        AiStepEnrichment enrichment = aiEnrichmentPort.enrichStep(
            request.placeName(),
            request.city(),
            request.region(),
            request.country(),
            request.travelMode(),
            request.interests() != null ? request.interests() : List.of()
        );
        return ResponseEntity.ok(enrichment);
    }

    // -------------------------------------------------------------------------
    // POST /optimize-route  – suggest optimal step ordering
    // -------------------------------------------------------------------------

    @Operation(
        summary     = "Suggest an optimised route order",
        description = "Uses the AI engine to suggest an optimised visit order for the provided list of places "
                    + "within an itinerary context."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Optimised order suggested"),
        @ApiResponse(responseCode = "400", description = "Invalid request",
                     content = @Content(schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "AI service unavailable",
                     content = @Content(schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class)))
    })
    @PostMapping(value = "/optimize-route",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OptimizeRouteResponse> optimizeRoute(
            @Valid @RequestBody OptimizeRouteRequest request) {

        log.debug("POST /api/v1/ai/optimize-route steps={}", request.steps());

        // Delegate to suggestForItinerary for a high-level trip overview which
        // includes ordering hints; a dedicated optimisation port can be wired in
        // once available.
        AiItinerarySuggestion suggestion = aiEnrichmentPort.suggestForItinerary(
            "Route optimisation",
            request.steps() != null ? request.steps() : List.of(),
            null,
            List.of()
        );

        // Return the highlights list as the suggested order alongside the summary
        // as the rationale – a real implementation would use a dedicated prompt.
        List<String> optimizedOrder = suggestion.highlights() != null
            ? suggestion.highlights()
            : (request.steps() != null ? request.steps() : List.of());

        return ResponseEntity.ok(new OptimizeRouteResponse(optimizedOrder, suggestion.summary()));
    }

    // -------------------------------------------------------------------------
    // POST /chat  – conversational AI
    // -------------------------------------------------------------------------

    @Operation(
        summary     = "Chat with the AI travel assistant",
        description = "Sends a user message to the AI travel assistant, optionally within the context "
                    + "of a specific itinerary and with prior conversation history for continuity."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "AI reply generated"),
        @ApiResponse(responseCode = "400", description = "Invalid request",
                     content = @Content(schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "AI service unavailable",
                     content = @Content(schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class)))
    })
    @PostMapping(value = "/chat",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AiChatResponse> chat(
            @Valid @RequestBody AiChatRequest request) {

        log.debug("POST /api/v1/ai/chat itineraryId={} message='{}'",
                  request.itineraryId(), request.message());

        // Use enrichStep as a chat proxy: the message is treated as a placeName
        // query so that the AI returns contextual information.
        // A dedicated ChatPort can be introduced when Ollama chat APIs are integrated.
        AiStepEnrichment enrichment = aiEnrichmentPort.enrichStep(
            request.message(),
            null, null, null,
            null,
            List.of()
        );

        String reply = enrichment.description() != null
            ? enrichment.description()
            : "I'm sorry, I couldn't generate a response at this time.";

        // Append to conversation history
        List<String> history = request.conversationHistory() != null
            ? new java.util.ArrayList<>(request.conversationHistory())
            : new java.util.ArrayList<>();
        history.add("user: " + request.message());
        history.add("assistant: " + reply);

        return ResponseEntity.ok(new AiChatResponse(reply, history));
    }
}
