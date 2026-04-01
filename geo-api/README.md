# Geo API

## Overview

Specialized domain API module for geographic operations in the hexagonal architecture. Defines contracts for geocoding, POI discovery, and route calculation services, plus worker use cases for background itinerary enrichment.

## Purpose

Defines contracts for:
- Forward and reverse geocoding
- Point of Interest (POI) discovery
- Turn-by-turn routing calculations
- Background workers that enrich itinerary steps with geographic data

## Architecture Role

**Hexagonal Layer**: Ports (Contracts)
- **Inbound Ports**: Worker use cases for async processing
- **Outbound Ports**: SPIs for external geographic services + cross-domain persistence

## Module Structure

```
geo-api/
├── port/
│   ├── inbound/          # Worker use cases
│   ├── outbound/         # Cross-domain persistence port
│   ├── GeocodingPort.java
│   ├── PoiDiscoveryPort.java
│   └── RoutingPort.java
└── dto/                  # Request/Response DTOs
```

## Inbound Ports (Worker Use Cases)

Located in `port/inbound/`:

### GeoWorkerUseCase
```java
void processGeocoding(UUID itineraryId, String accessToken);
```
- Geocodes all steps of an itinerary
- Updates each step with latitude, longitude, and full address components
- First step in enrichment pipeline

### RouteWorkerUseCase
```java
void processRouting(UUID itineraryId, String accessToken);
```
- Calculates routes between consecutive steps
- Updates distance (km), duration (minutes), and GeoJSON geometry
- Second step in enrichment pipeline
- Uses travel mode from itinerary (driving, walking, cycling)

### PoiWorkerUseCase
```java
void processPoi(UUID itineraryId, String accessToken);
```
- Discovers points of interest near each step
- Updates POI list for each step
- Marks itinerary status as `COMPLETED`
- Publishes `ItineraryCompletedEvent`
- Final step in enrichment pipeline

## Outbound Ports (Geographic Services)

### GeocodingPort
```java
public interface GeocodingPort {
    /**
     * Forward geocoding: place name → coordinates
     */
    Optional<GeoResult> geocode(String query, String countryCode);

    /**
     * Reverse geocoding: coordinates → address
     */
    Optional<GeoResult> reverseGeocode(double latitude, double longitude);
}
```

**Implementations:**
- Nominatim (OpenStreetMap)
- Google Geocoding API
- HERE Geocoding API
- Photon (local geocoding)

### RoutingPort
```java
public interface RoutingPort {
    /**
     * Calculate route between two coordinates
     */
    Optional<RouteSegment> calculateRoute(
        double fromLat, double fromLon,
        double toLat, double toLon,
        String travelMode  // "driving", "walking", "cycling"
    );
}
```

**Implementations:**
- OSRM (Open Source Routing Machine)
- GraphHopper
- Valhalla
- Google Directions API

### PoiDiscoveryPort
```java
public interface PoiDiscoveryPort {
    /**
     * Find points of interest near coordinates
     */
    List<PoiResult> findPoisNear(
        double latitude,
        double longitude,
        int radiusMeters,
        String category  // "tourism", "food", "lodging", etc.
    );
}
```

**Implementations:**
- Overpass API (OpenStreetMap)
- Google Places API
- Foursquare Places API

## Outbound Persistence Port

### ItineraryStepDataPort

Cross-domain persistence adapter allowing geo workers to read/write itinerary step data without depending on itinerary-impl module.

```java
public interface ItineraryStepDataPort {
    record StepData(
        UUID id,
        int stepOrder,
        String placeName,
        Double latitude,
        Double longitude,
        String city,
        String region,
        String country
    ) {}

    // Read operations
    List<StepData> findStepsByItineraryId(UUID itineraryId);
    String getTravelMode(UUID itineraryId);

    // Write operations (geocoding results)
    void updateStepWithGeocodingResult(
        UUID itineraryId,
        int stepOrder,
        GeoResult geocodingResult
    );

    // Write operations (routing results)
    void updateStepWithRoutingResult(
        UUID itineraryId,
        int stepOrder,
        RouteSegment route
    );

    // Write operations (POI results)
    void updateStepWithPoiResults(
        UUID itineraryId,
        int stepOrder,
        List<PoiResult> pois
    );

    // Aggregate operations
    void updateItineraryTotals(
        UUID itineraryId,
        double totalDistanceKm,
        int totalDurationMinutes
    );

    void markItineraryCompleted(UUID itineraryId);
    void markItineraryProcessing(UUID itineraryId);
}
```

**Purpose**: Provides an anti-corruption layer between geo domain and itinerary domain.

**Implementations:**
- **Monolith**: Direct delegation to `ItineraryPersistencePort`
- **Microservice**: gRPC/REST client calling itinerary service

## DTOs

### GeoSearchDTO

**Request:**
```java
record Request(
    @NotBlank String query,
    String countryCode,         // Optional ISO 3166-1 alpha-2
    @Min(1) @Max(50) int limit  // Default: 5
)
```

**Response:**
```java
record Response(List<GeoResult> results)

record GeoResult(
    String displayName,
    double latitude,
    double longitude,
    Long osmId,
    String city,
    String province,
    String region,
    String country,
    String countryCode,
    String type  // "city", "street", "building", etc.
)
```

### RouteDTO

**Request:**
```java
record Request(
    @DecimalMin("-90.0") @DecimalMax("90.0") double fromLat,
    @DecimalMin("-180.0") @DecimalMax("180.0") double fromLon,
    @DecimalMin("-90.0") @DecimalMax("90.0") double toLat,
    @DecimalMin("-180.0") @DecimalMax("180.0") double toLon,
    @NotBlank String travelMode  // "driving", "walking", "cycling"
)
```

**Response:**
```java
record Response(
    double distanceKm,
    int durationMinutes,
    String geoJsonGeometry,  // LineString GeoJSON
    boolean fromFallback     // True if routing service unavailable
)
```

### PoiDTO

**Request:**
```java
record Request(
    @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
    @DecimalMin("-180.0") @DecimalMax("180.0") double longitude,
    @Min(100) @Max(5000) int radiusMeters,  // Default: 1000
    String category  // "tourism", "food", "lodging", etc.
)
```

**Response:**
```java
record Response(List<PoiItem> pois)

record PoiItem(
    Long osmId,
    String name,
    String category,
    double latitude,
    double longitude,
    Map<String, String> tags  // OSM tags (e.g., cuisine=italian)
)
```

## Value Objects (from itinerary-api)

This module depends on `itinerary-api` and reuses its value objects:

- `com.travel.itinerary.api.vo.GeoResult`
- `com.travel.itinerary.api.vo.PoiResult`
- `com.travel.itinerary.api.vo.RouteSegment`

## Dependencies

```xml
<!-- Cross-domain dependency -->
<dependency>
    <groupId>com.travel</groupId>
    <artifactId>itinerary-api</artifactId>
</dependency>

<!-- JSON Serialization -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>

<!-- Bean Validation -->
<dependency>
    <groupId>jakarta.validation</groupId>
    <artifactId>jakarta.validation-api</artifactId>
</dependency>

<!-- Lombok -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <scope>provided</scope>
</dependency>
```

## Hexagonal Architecture Patterns

### 1. Worker Pattern

Workers are background processors triggered by events:

```
ItineraryCreatedEvent published
    ↓
GeoWorkerUseCase.processGeocoding()
    ↓ geocode all steps
RouteWorkerUseCase.processRouting()
    ↓ calculate routes
PoiWorkerUseCase.processPoi()
    ↓ discover POIs
ItineraryCompletedEvent published
```

### 2. Cross-Domain Communication

**Monolith (in-process):**
```
GeoWorker → ItineraryStepDataPort → Direct method call → ItineraryService
```

**Microservice (remote):**
```
GeoWorker → ItineraryStepDataPort → gRPC client → Itinerary Microservice
```

### 3. Status Transitions

Workers coordinate itinerary status:
- `GeoWorker`: Marks as `PROCESSING`
- `PoiWorker`: Marks as `COMPLETED`

### 4. Technology Agnostic

All ports are pure Java interfaces:
- No Spring annotations
- No HTTP/gRPC specifics
- No persistence framework coupling

### 5. Pluggable External Services

Each port can have multiple implementations:
- **Nominatim** (free, rate-limited)
- **Google** (commercial, high quality)
- **Self-hosted OSRM** (unlimited, infrastructure cost)

## Usage by Other Modules

### Domain Implementations

**geo-impl-core**: Business logic implementing worker use cases

**geo-impl-http**: HTTP client adapters for:
- `PhotonAdapter` → `GeocodingPort`
- `NominatimAdapter` → `GeocodingPort`
- `OsrmAdapter` → `RoutingPort`
- `OverpassAdapter` → `PoiDiscoveryPort`

**geo-impl-kafka**: Kafka consumer listening for `ItineraryCreatedEvent`

### Application Assembly

**app-monolith**:
```xml
<dependency>
    <groupId>com.travel</groupId>
    <artifactId>geo-impl-core</artifactId>
</dependency>
<dependency>
    <groupId>com.travel</groupId>
    <artifactId>geo-impl-http</artifactId>
</dependency>
<!-- Workers run in-process, no Kafka needed -->
```

**app-microservice-geo** (hypothetical):
```xml
<dependency>
    <groupId>com.travel</groupId>
    <artifactId>geo-impl-core</artifactId>
</dependency>
<dependency>
    <groupId>com.travel</groupId>
    <artifactId>geo-impl-http</artifactId>
</dependency>
<dependency>
    <groupId>com.travel</groupId>
    <artifactId>geo-impl-kafka</artifactId>
</dependency>
<!-- Workers triggered by Kafka events -->
```

## Enrichment Pipeline

```
1. Client creates itinerary
   ↓
2. ItineraryCreatedEvent published
   ↓
3. GeoWorker geocodes all steps (lat/lon, address)
   ├─ Calls GeocodingPort for each step
   └─ Updates via ItineraryStepDataPort
   ↓
4. RouteWorker calculates routes between steps
   ├─ Calls RoutingPort for each pair
   └─ Updates via ItineraryStepDataPort
   ↓
5. PoiWorker discovers nearby attractions
   ├─ Calls PoiDiscoveryPort for each step
   ├─ Updates via ItineraryStepDataPort
   └─ Marks itinerary COMPLETED
   ↓
6. ItineraryCompletedEvent published
   ↓
7. Client retrieves enriched itinerary
```

## Rate Limiting

External services often have rate limits:

| Service | Limit | Strategy |
|---------|-------|----------|
| Nominatim | 1 req/sec | Circuit breaker, local cache |
| OSRM Public | None | Direct calls |
| Overpass API | None | Direct calls |

Implementations should include:
- **Circuit breakers** (Resilience4j)
- **Caching** (Redis/Caffeine)
- **Retry policies** (exponential backoff)

## Error Handling

Workers must be idempotent:
- Partial processing can be retried
- Cache geocoding results to avoid re-querying
- Fallback to default values if service unavailable
