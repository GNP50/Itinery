# AI API

## Overview

Specialized domain API module for AI-powered enrichment in the hexagonal architecture. Defines contracts for generating travel descriptions, tips, suggestions, and conversational chat grounded in itinerary context.

## Purpose

Defines contracts for:
- Step-level AI enrichment (descriptions, tips, local recommendations)
- Itinerary-level AI suggestions
- Conversational chat with itinerary context
- Route optimization via AI algorithms

## Architecture Role

**Hexagonal Layer**: Ports (Contracts)
- **Inbound Ports**: Worker use cases for async AI processing
- **Outbound Ports**: SPIs for AI services + cross-domain persistence

## Module Structure

```
ai-api/
├── port/
│   ├── inbound/              # Worker use cases
│   ├── outbound/             # Cross-domain persistence port
│   ├── AiEnrichmentPort.java
│   ├── AiChatPort.java
│   └── AiRouteOptimizerPort.java
├── dto/                      # Request/Response DTOs
└── vo/                       # Value Objects
```

## Inbound Ports (Worker Use Case)

Located in `port/inbound/`:

### AiWorkerUseCase
```java
void enrichSteps(UUID itineraryId, String accessToken);
```
- Enriches all steps with AI-generated content
- Generates descriptions, travel tips, must-see attractions
- Suggests local food and recommended duration
- Uses user preferences JSON for personalization
- Triggered after geographic enrichment completes

## Outbound Ports (AI Services)

### AiEnrichmentPort
```java
public interface AiEnrichmentPort {
    /**
     * Generate enrichment content for a single step
     */
    AiStepEnrichment enrichStep(
        String placeName,
        String city,
        String region,
        String country,
        String travelMode,
        String userPreferencesJson
    );

    /**
     * Generate itinerary-level suggestions
     */
    AiItinerarySuggestion generateItinerarySuggestions(
        String itineraryTitle,
        List<String> placeNames,
        String travelMode,
        String userPreferencesJson
    );
}
```

**Implementations:**
- Ollama (local LLM)
- OpenAI GPT-4
- Anthropic Claude
- Google Gemini

### AiChatPort
```java
public interface AiChatPort {
    /**
     * Conversational turn-taking with itinerary context
     */
    String chat(
        String userMessage,
        String itineraryContextJson,
        List<ChatMessage> conversationHistory
    );
}

record ChatMessage(String role, String content) {}  // "user" or "assistant"
```

**Implementations:**
- OpenAI Chat API
- Anthropic Messages API
- Local LLM via Ollama

### AiRouteOptimizerPort
```java
public interface AiRouteOptimizerPort {
    /**
     * Optimize visit order using AI/combinatorial algorithms
     */
    AiRouteOptimizationDTO.Response optimizeRoute(
        UUID itineraryId,
        List<String> placeNames,
        List<GeoCoordinate> coordinates
    );
}
```

**Implementations:**
- AI-powered TSP solver
- Genetic algorithm
- Simulated annealing
- Google OR-Tools

## Outbound Persistence Port

### ItineraryStepDataPort

Cross-domain persistence adapter for AI workers.

```java
public interface ItineraryStepDataPort {
    record StepData(
        UUID id,
        int stepOrder,
        String placeName,
        String city,
        String region,
        String country,
        Double latitude,
        Double longitude
    ) {}

    // Read operations
    List<StepData> findStepsByItineraryId(UUID itineraryId);
    String getTravelMode(UUID itineraryId);
    String getItineraryTitle(UUID itineraryId);
    boolean isAiTipsEnabled(UUID itineraryId);

    // Write operations
    void updateStepWithAiEnrichment(
        UUID itineraryId,
        int stepOrder,
        AiStepEnrichment enrichment
    );

    void updateItineraryAiSuggestions(
        UUID itineraryId,
        String suggestionsJson
    );
}
```

## DTOs

### AiSuggestionDTO

**Request:**
```java
record Request(
    @NotNull UUID itineraryId,
    @NotBlank String accessToken,
    @NotBlank String placeName,
    String city,
    String region,
    String country,
    String travelMode,
    String interests  // Comma-separated: "history,food,nature"
)
```

**Response:**
```java
record Response(
    String description,          // 2-3 paragraph overview
    List<String> tips,           // 3-5 practical tips
    List<String> mustSee,        // Top 3-5 attractions
    List<String> localFood,      // 2-3 local dishes to try
    String recommendedDuration   // "2-3 hours", "Half day", etc.
)
```

### AiRouteOptimizationDTO

**Request:**
```java
record Request(
    @NotNull UUID itineraryId,
    @NotBlank String accessToken,
    @NotEmpty List<String> placeNames
)
```

**Response:**
```java
record Response(
    List<OptimizedStep> optimizedOrder,
    String reasoning,              // AI explanation of changes
    double estimatedTotalKm
)

record OptimizedStep(
    int originalIndex,
    int newIndex,
    String placeName
)
```

### AiChatDTO

**Request:**
```java
record Request(
    @NotNull UUID itineraryId,
    @NotBlank String accessToken,
    @NotBlank String message,
    List<ChatMessage> history  // Optional conversation context
)
```

**Response:**
```java
record Response(
    String reply,
    String conversationId  // For maintaining context
)
```

## Value Objects

Located in `vo/`:

### AiStepEnrichment
```java
record AiStepEnrichment(
    String description,
    List<String> tips,
    List<String> mustSee,
    List<String> localFood,
    String recommendedDuration
)
```

### AiItinerarySuggestion
```java
record AiItinerarySuggestion(
    String overallTheme,
    List<String> packingList,
    Map<String, String> culturalTips,  // e.g., "Tipping" → "10-15% standard"
    String bestTimeToVisit
)
```

## Dependencies

```xml
<!-- Self-contained module - NO dependency on itinerary-api -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>

<dependency>
    <groupId>jakarta.validation</groupId>
    <artifactId>jakarta.validation-api</artifactId>
</dependency>

<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <scope>provided</scope>
</dependency>
```

## Hexagonal Architecture Patterns

### 1. Worker Pattern

AI worker triggered after geographic enrichment:

```
ItineraryCompletedEvent (from geo workers)
    ↓
AiWorkerUseCase.enrichSteps()
    ↓ for each step
AiEnrichmentPort.enrichStep()
    ↓ update via persistence port
ItineraryStepDataPort.updateStepWithAiEnrichment()
```

### 2. Context Injection

Chat port receives pre-computed context:

```java
String context = buildItineraryContext(itinerary);  // JSON string
String reply = aiChatPort.chat(userMessage, context, history);
```

This allows AI service to ground responses in itinerary data without requiring API access.

### 3. Preference Forwarding

User preferences guide AI generation:

```json
{
  "interests": ["history", "food", "photography"],
  "pace": "relaxed",
  "budget": "moderate",
  "dietary": "vegetarian"
}
```

AI service uses preferences to personalize descriptions and recommendations.

### 4. Multiple AI Concerns

Three separate ports for different AI responsibilities:
- **Enrichment**: Content generation
- **Chat**: Conversational interface
- **Optimization**: Algorithmic route planning

Each can use different AI providers or strategies.

### 5. Technology Agnostic

All ports are pure Java interfaces:
- No LLM library coupling
- No API-specific types
- Swappable implementations (local LLM ↔ cloud API)

## Usage by Other Modules

### Domain Implementations

**ai-impl-core**: Business logic implementing worker use case

**ai-impl-ollama**: HTTP client adapter for:
- `OllamaHttpAdapter` → `AiEnrichmentPort`
- `OllamaHttpAdapter` → `AiChatPort`

**ai-impl-kafka**: Kafka consumer listening for enrichment triggers

### Application Assembly

**app-monolith**:
```xml
<dependency>
    <groupId>com.travel</groupId>
    <artifactId>ai-impl-core</artifactId>
</dependency>
<dependency>
    <groupId>com.travel</groupId>
    <artifactId>ai-impl-ollama</artifactId>
</dependency>
<!-- Workers run in-process -->
```

**app-microservice-ai**:
```xml
<dependency>
    <groupId>com.travel</groupId>
    <artifactId>ai-impl-core</artifactId>
</dependency>
<dependency>
    <groupId>com.travel</groupId>
    <artifactId>ai-impl-ollama</artifactId>
</dependency>
<dependency>
    <groupId>com.travel</groupId>
    <artifactId>ai-impl-kafka</artifactId>
</dependency>
<dependency>
    <groupId>com.travel</groupId>
    <artifactId>ai-impl-grpc-server</artifactId>
</dependency>
<!-- Standalone microservice with gRPC API -->
```

## AI Enrichment Pipeline

```
1. Geographic enrichment completes
   ↓
2. ItineraryCompletedEvent published (or custom trigger)
   ↓
3. AiWorker enriches each step sequentially
   ├─ Calls AiEnrichmentPort.enrichStep()
   │  ├─ LLM generates: description, tips, must-see, food
   │  └─ Returns AiStepEnrichment value object
   └─ Updates via ItineraryStepDataPort
   ↓
4. AiWorker generates itinerary-level suggestions
   ├─ Calls AiEnrichmentPort.generateItinerarySuggestions()
   └─ Updates via ItineraryStepDataPort
   ↓
5. Client retrieves fully enriched itinerary
```

## Prompt Engineering

Implementations should use structured prompts:

**Example for Step Enrichment:**
```
You are a travel guide. Provide enrichment for:
Place: {placeName}
Location: {city}, {region}, {country}
Travel Mode: {travelMode}
User Interests: {interests}

Generate:
1. Description (2-3 paragraphs)
2. Tips (3-5 practical travel tips)
3. Must-See (top 3-5 attractions)
4. Local Food (2-3 dishes to try)
5. Recommended Duration (e.g., "2-3 hours")

Respond in JSON format.
```

## LLM Model Selection

Recommended models by use case:

| Use Case | Model | Why |
|----------|-------|-----|
| Step Enrichment | GPT-4, Claude Sonnet | High-quality prose, cultural knowledge |
| Chat | GPT-4 Turbo, Llama 3 | Fast response, conversational |
| Route Optimization | GPT-4, Gemini Pro | Strong reasoning, math |
| Budget Option | Ollama (Llama 3) | Free, runs locally, good quality |

## Rate Limiting & Costs

| Provider | Cost (per 1M tokens) | Rate Limit |
|----------|---------------------|------------|
| OpenAI GPT-4 | $30 input / $60 output | 10,000 RPM |
| Anthropic Claude | $15 input / $75 output | Varies |
| Google Gemini | $7 input / $21 output | 60 RPM |
| Ollama (local) | $0 | Unlimited |

Implementations should:
- Cache enrichment results to avoid re-querying
- Use cheaper models for less critical content
- Batch requests when possible
- Implement exponential backoff

## Error Handling

AI workers should be resilient:
- **Timeout**: AI calls may take 10-30 seconds
- **Fallback**: Use generic content if LLM unavailable
- **Retry**: Exponential backoff for transient errors
- **Validation**: Ensure AI responses match expected structure

**Example Fallback:**
```java
AiStepEnrichment enrichment;
try {
    enrichment = aiPort.enrichStep(...);
} catch (AiServiceException e) {
    // Fallback to generic content
    enrichment = new AiStepEnrichment(
        "A notable destination in " + city,
        List.of("Check local weather", "Respect local customs"),
        List.of(),
        List.of(),
        "Several hours"
    );
}
```

## Future Enhancements

Potential additional ports:
- **AiImageGenerationPort**: Create destination images
- **AiTranslationPort**: Translate itineraries to user's language
- **AiSentimentPort**: Analyze user feedback sentiment
- **AiRecommendationPort**: Suggest similar itineraries
