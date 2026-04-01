package com.travel.ai.impl.adapter.outbound.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.travel.ai.api.port.AiEnrichmentPort;
import com.travel.ai.api.vo.AiItinerarySuggestion;
import com.travel.ai.api.vo.AiStepEnrichment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Outbound adapter that fulfils {@link AiEnrichmentPort} by calling an Ollama
 * instance (cloud or local) via its REST API, or by returning realistic mock
 * responses for demo/development purposes.
 *
 * <p>Active mode is determined at startup from environment variables:
 * <ol>
 *   <li><b>Cloud</b> – {@code OLLAMA_CLOUD_URL} is set to a non-blank URL.</li>
 *   <li><b>Mock</b>  – {@code OLLAMA_CLOUD_URL} is empty and {@code MOCK_AI_CHAT=true}.</li>
 *   <li><b>Disabled</b> – both unset; returns empty enrichments (graceful degradation).</li>
 * </ol>
 *
 * <p>In cloud and local modes, any exception (HTTP error, JSON parse failure,
 * timeout) is caught, logged at WARN level, and an empty enrichment object is
 * returned instead of propagating the failure to the caller.
 */
@Slf4j
@Component
public class OllamaAdapter implements AiEnrichmentPort {

    private static final String SERVICE_NAME  = "Ollama";
    private static final String GENERATE_PATH = "/api/generate";

    private enum Mode { CLOUD, MOCK, DISABLED }

    private final Mode         mode;
    private final String       baseUrl;
    private final String       model;
    private final String       apiKey;
    private final Duration     requestTimeout;
    private final HttpClient   httpClient;
    private final ObjectMapper objectMapper;

    public OllamaAdapter(
            @Value("${ollama.cloud-url:}")        String cloudUrl,
            @Value("${ollama.model:llama3.1:8b}") String model,
            @Value("${ollama.api-key:}")          String apiKey,
            @Value("${ollama.timeout-seconds:30}") int timeoutSeconds,
            @Value("${ai.mock-chat:false}")        boolean mockChat,
            ObjectMapper objectMapper) {

        this.model         = model;
        this.apiKey        = apiKey;
        this.objectMapper  = objectMapper;
        this.requestTimeout = Duration.ofSeconds(timeoutSeconds);

        if (cloudUrl != null && !cloudUrl.isBlank()) {
            this.mode    = Mode.CLOUD;
            this.baseUrl = cloudUrl;
        } else if (mockChat) {
            this.mode    = Mode.MOCK;
            this.baseUrl = null;
        } else {
            this.mode    = Mode.DISABLED;
            this.baseUrl = null;
        }

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        log.info("OllamaAdapter configured: mode={} baseUrl={} model={} apiKeySet={} timeoutSeconds={}",
                mode, baseUrl != null ? baseUrl : "n/a", model, 
                apiKey != null && !apiKey.isBlank() ? "yes" : "no", timeoutSeconds);
    }

    // -------------------------------------------------------------------------
    // AiEnrichmentPort
    // -------------------------------------------------------------------------

    @Override
    public AiStepEnrichment enrichStep(String placeName,
                                       String city,
                                       String region,
                                       String country,
                                       String travelMode,
                                       List<String> interests) {
        if (mode == Mode.MOCK) {
            return mockEnrichStep(placeName, city, country, travelMode, interests);
        }
        if (mode == Mode.DISABLED) {
            log.debug("[{}] AI disabled – returning empty step enrichment for place='{}'",
                    SERVICE_NAME, placeName);
            return emptyStepEnrichment();
        }
        try {
            String prompt = buildStepPrompt(placeName, city, region, country,
                    travelMode, interests);
            String rawResponse = callOllama(prompt);
            return parseStepEnrichment(rawResponse);
        } catch (Exception ex) {
            log.warn("[{}] enrichStep failed for place='{}': {}",
                    SERVICE_NAME, placeName, ex.getMessage());
            return emptyStepEnrichment();
        }
    }

    @Override
    public AiItinerarySuggestion suggestForItinerary(String title,
                                                      List<String> places,
                                                      String travelMode,
                                                      List<String> interests) {
        if (mode == Mode.MOCK) {
            return mockSuggestForItinerary(title, places, travelMode, interests);
        }
        if (mode == Mode.DISABLED) {
            log.debug("[{}] AI disabled – returning empty itinerary suggestion for title='{}'",
                    SERVICE_NAME, title);
            return emptyItinerarySuggestion();
        }
        try {
            String prompt = buildItineraryPrompt(title, places, travelMode, interests);
            String rawResponse = callOllama(prompt);
            return parseItinerarySuggestion(rawResponse);
        } catch (Exception ex) {
            log.warn("[{}] suggestForItinerary failed for title='{}': {}",
                    SERVICE_NAME, title, ex.getMessage());
            return emptyItinerarySuggestion();
        }
    }

    // -------------------------------------------------------------------------
    // Mock implementations
    // -------------------------------------------------------------------------

    private AiStepEnrichment mockEnrichStep(String placeName, String city,
                                             String country, String travelMode,
                                             List<String> interests) {
        String interestsStr = (interests != null && !interests.isEmpty())
                ? String.join(" and ", interests) : "general sightseeing";

        String description = String.format(
                "%s is a must-visit destination in %s, %s. " +
                "Perfect for travellers interested in %s, it offers a blend of culture, " +
                "history, and local charm that makes every visit memorable.",
                safe(placeName), safe(city), safe(country), interestsStr);

        List<String> tips = List.of(
                "Visit early in the morning to avoid peak crowds and enjoy better photos.",
                "Check the local opening hours in advance – many attractions close on Mondays.",
                String.format("Getting there by %s is the most convenient option from the city centre.",
                        safe(travelMode)));

        List<String> mustSee = List.of(
                String.format("The main square and historic heart of %s", safe(placeName)),
                String.format("Local market showcasing authentic %s cuisine and crafts", safe(city)));

        String localFood = String.format(
                "Try the local specialties at a traditional %s trattoria near the main square.", safe(city));

        String recommendedDuration = "2-3 hours";

        log.debug("[{}][MOCK] enrichStep for place='{}'", SERVICE_NAME, placeName);
        return new AiStepEnrichment(description, tips, mustSee, localFood, recommendedDuration);
    }

    private AiItinerarySuggestion mockSuggestForItinerary(String title, List<String> places,
                                                           String travelMode, List<String> interests) {
        String placesStr = (places != null && !places.isEmpty())
                ? String.join(", ", places) : "various destinations";
        String interestsStr = (interests != null && !interests.isEmpty())
                ? String.join(" and ", interests) : "exploration";

        String summary = String.format(
                "Your itinerary \"%s\" takes you through %s, offering a rich journey " +
                "tailored for lovers of %s. Travelling by %s, you'll experience a perfect " +
                "mix of iconic landmarks and hidden gems that make this trip truly unforgettable.",
                safe(title), placesStr, interestsStr, safe(travelMode));

        List<String> highlights = new ArrayList<>();
        if (places != null) {
            for (int i = 0; i < Math.min(places.size(), 3); i++) {
                highlights.add("Discovering the unique character of " + places.get(i));
            }
        }
        if (highlights.isEmpty()) {
            highlights = List.of(
                    "Immersive cultural experiences at every stop",
                    "Scenic routes connecting historical landmarks",
                    "Authentic local dining and artisan markets");
        }

        String estimatedBudget = "€120–€200 per person (excluding accommodation)";

        List<String> generalTips = List.of(
                "Book major attractions in advance to skip queues.",
                "A city pass or regional travel card can save significant costs on transport.",
                "Pack comfortable walking shoes — most highlights are best explored on foot.");

        log.debug("[{}][MOCK] suggestForItinerary for title='{}'", SERVICE_NAME, title);
        return new AiItinerarySuggestion(summary, Collections.unmodifiableList(highlights),
                estimatedBudget, generalTips);
    }

    // -------------------------------------------------------------------------
    // HTTP call
    // -------------------------------------------------------------------------

    private String callOllama(String prompt) throws Exception {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model",  model);
        requestBody.put("prompt", prompt);
        requestBody.put("stream", false);

        String requestJson = objectMapper.writeValueAsString(requestBody);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(baseUrl + GENERATE_PATH))
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .header("Content-Type", "application/json")
                .header("Accept",       "application/json")
                .timeout(requestTimeout);

        // Add Authorization header if API key is provided (for Ollama Cloud)
        if (apiKey != null && !apiKey.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }

        HttpRequest request = requestBuilder.build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new AiEnrichmentException(
                    "Ollama returned HTTP " + response.statusCode()
                            + ": " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String modelResponse = root.path("response").asText(null);
        if (modelResponse == null || modelResponse.isBlank()) {
            throw new AiEnrichmentException("Ollama returned an empty response");
        }

        return modelResponse;
    }

    // -------------------------------------------------------------------------
    // Prompt builders
    // -------------------------------------------------------------------------

    private static String buildStepPrompt(String placeName, String city,
                                           String region,    String country,
                                           String travelMode, List<String> interests) {

        String interestsStr = (interests != null && !interests.isEmpty())
                ? String.join(", ", interests) : "general sightseeing";

        return """
                You are a knowledgeable travel guide. Provide enrichment for the following place.
                Respond ONLY with a valid JSON object — no markdown, no extra text.

                Place: %s
                City: %s
                Region: %s
                Country: %s
                Travel mode: %s
                Traveller interests: %s

                Required JSON format:
                {
                  "description": "<2-3 sentence narrative description tailored to the interests>",
                  "tips": ["<tip 1>", "<tip 2>", "<tip 3>"],
                  "must_see": ["<highlight 1>", "<highlight 2>"],
                  "local_food": "<local food or restaurant recommendation>",
                  "recommended_duration": "<e.g. 1-2 hours>"
                }
                """.formatted(
                        safe(placeName), safe(city), safe(region),
                        safe(country),  safe(travelMode), interestsStr);
    }

    private static String buildItineraryPrompt(String title, List<String> places,
                                                String travelMode, List<String> interests) {

        String placesStr    = (places != null) ? String.join(", ", places) : "N/A";
        String interestsStr = (interests != null && !interests.isEmpty())
                ? String.join(", ", interests) : "general sightseeing";

        return """
                You are an expert travel planner. Provide a high-level overview for the following itinerary.
                Respond ONLY with a valid JSON object — no markdown, no extra text.

                Itinerary title: %s
                Places visited (in order): %s
                Travel mode: %s
                Traveller interests: %s

                Required JSON format:
                {
                  "summary": "<1-paragraph narrative overview of the itinerary>",
                  "highlights": ["<highlight 1>", "<highlight 2>", "<highlight 3>"],
                  "estimated_budget": "<e.g. €150–€250 per person>",
                  "general_tips": ["<tip 1>", "<tip 2>", "<tip 3>"]
                }
                """.formatted(safe(title), placesStr, safe(travelMode), interestsStr);
    }

    // -------------------------------------------------------------------------
    // Response parsers
    // -------------------------------------------------------------------------

    private AiStepEnrichment parseStepEnrichment(String raw) {
        try {
            String json = extractJson(raw);
            JsonNode node = objectMapper.readTree(json);

            String description         = node.path("description").asText(null);
            List<String> tips          = parseStringArray(node.path("tips"));
            List<String> mustSee       = parseStringArray(node.path("must_see"));
            String localFood           = node.path("local_food").asText(null);
            String recommendedDuration = node.path("recommended_duration").asText(null);

            return new AiStepEnrichment(description, tips, mustSee,
                    localFood, recommendedDuration);

        } catch (Exception ex) {
            log.warn("[{}] Failed to parse step enrichment JSON: {}", SERVICE_NAME, ex.getMessage());
            return emptyStepEnrichment();
        }
    }

    private AiItinerarySuggestion parseItinerarySuggestion(String raw) {
        try {
            String json = extractJson(raw);
            JsonNode node = objectMapper.readTree(json);

            String       summary         = node.path("summary").asText(null);
            List<String> highlights      = parseStringArray(node.path("highlights"));
            String       estimatedBudget = node.path("estimated_budget").asText(null);
            List<String> generalTips     = parseStringArray(node.path("general_tips"));

            return new AiItinerarySuggestion(summary, highlights,
                    estimatedBudget, generalTips);

        } catch (Exception ex) {
            log.warn("[{}] Failed to parse itinerary suggestion JSON: {}", SERVICE_NAME, ex.getMessage());
            return emptyItinerarySuggestion();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String extractJson(String text) {
        int start = text.indexOf('{');
        int end   = text.lastIndexOf('}');
        if (start == -1 || end == -1 || end <= start) {
            throw new IllegalArgumentException(
                    "No JSON object found in model response: " + text);
        }
        return text.substring(start, end + 1);
    }

    private static List<String> parseStringArray(JsonNode arrayNode) {
        if (arrayNode == null || arrayNode.isMissingNode() || !arrayNode.isArray()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : arrayNode) {
            String value = item.asText(null);
            if (value != null && !value.isBlank()) {
                result.add(value);
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static String safe(String value) {
        return value != null ? value : "unknown";
    }

    private static AiStepEnrichment emptyStepEnrichment() {
        return new AiStepEnrichment(null, Collections.emptyList(),
                Collections.emptyList(), null, null);
    }

    private static AiItinerarySuggestion emptyItinerarySuggestion() {
        return new AiItinerarySuggestion(null, Collections.emptyList(),
                null, Collections.emptyList());
    }
}
