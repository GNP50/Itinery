# Itinerary API

## Overview

Core domain API module defining the contract layer for itinerary management in the hexagonal architecture. Contains use case ports, service provider interfaces (SPIs), DTOs, domain events, and value objects—all technology-agnostic.

## Purpose

Defines the contracts for:
- Itinerary CRUD operations (Create, Read, Update, Delete)
- Position tracking for active travelers
- Queue status monitoring
- Orchestration of enrichment workflows across geographic and AI services

## Architecture Role

**Hexagonal Layer**: Ports (Contracts)
- **Inbound Ports**: Use cases exposed to external adapters (REST, gRPC, CLI)
- **Outbound Ports**: SPIs for infrastructure adapters (persistence, messaging, caching, external services)

## Module Structure

```
itinerary-api/
├── port/
│   ├── inbound/          # Primary ports (Use Cases)
│   └── outbound/         # Secondary ports (SPIs)
├── dto/                  # Data Transfer Objects
├── event/                # Domain Events
└── vo/                   # Value Objects
```

## Inbound Ports (Use Cases)

Located in `port/inbound/`:

### CreateItineraryUseCase
```java
CreateResponse create(ItineraryDTO.Request request);
```
- Validates input, persists skeleton itinerary
- Enqueues for asynchronous background enrichment
- Returns access token for subsequent operations

### GetItineraryUseCase
```java
ItineraryDTO.Response getById(UUID id, String accessToken);
ItineraryDTO.StatusResponse getStatus(UUID id, String accessToken);
```
- Full retrieval with authentication via access token
- Lightweight status polling endpoint

### UpdateItineraryUseCase
```java
ItineraryDTO.Response update(UUID id, String accessToken, ItineraryDTO.Request request);
```
- Replace mutable fields (title, description, steps)
- Triggers re-enrichment if steps or travel mode changed

### DeleteItineraryUseCase
```java
void delete(UUID id, String accessToken);
```
- Permanently removes itinerary and associated data
- Authenticated via access token

### ListItinerariesUseCase
```java
ItineraryDTO.ListResponse list(int offset, int limit);
```
- List all itineraries owned by authenticated user
- Pagination support

### UpdatePositionUseCase
```java
PositionUpdateDTO.Response updatePosition(UUID id, String accessToken, PositionUpdateDTO.Request request);
```
- Records traveler's current GPS position
- Advances active step index for progress tracking

### QueryQueueUseCase
```java
QueueStatusDTO getQueueStatus();
```
- Administrative endpoint for queue statistics
- Returns pending count, processing count, average wait time

## Outbound Ports (SPIs)

Located in `port/outbound/`:

### Persistence

**ItineraryPersistencePort<T>**
```java
T save(T itinerary);
Optional<T> findById(UUID id);
Optional<T> findByIdAndToken(UUID id, String token);
List<T> findAll();
void delete(UUID id);
// + 8 specialized DTO projection queries
```
- Generic persistence abstraction
- Supports any aggregate type via parameterization
- Optimized read queries via DTO projections

### Geographic Services

**GeocodingPort**
```java
Optional<GeoResult> geocode(String query, String countryCode);
Optional<GeoResult> reverseGeocode(double lat, double lon);
```

**PoiDiscoveryPort**
```java
List<PoiResult> findPoisNear(double lat, double lon, int radiusMeters, String category);
```

**RoutingPort**
```java
Optional<RouteSegment> calculateRoute(double fromLat, double fromLon, double toLat, double toLon, String travelMode);
```

### Event Publishing

**EventPublisherPort**
```java
void publish(Object event);
```
- Publishes domain events asynchronously
- Implementations: Kafka, RabbitMQ, Spring ApplicationEventPublisher

### Caching

**CachePort**
```java
<V> Optional<V> get(String key);
<V> void put(String key, V value, Duration ttl);
void evict(String key);
```
- Generic TTL-based cache
- Implementations: Redis, Caffeine, Memcached

### Queue Management

**QueuePort<T>**
```java
void push(T item);
Optional<T> pop();
int size();
```

**QueueManagementPort**
```java
void enqueue(UUID itineraryId, String accessToken, int priority);
Optional<QueueStatusDTO.Item> dequeue();
QueueStatusDTO getStatus();
void cancel(UUID itineraryId);
```
- Durable FIFO queue with priority support
- Implementations: Redis Streams, RabbitMQ, Kafka

### File Storage

**FileStoragePort**
```java
String upload(String bucketName, String objectName, InputStream stream, String contentType);
InputStream download(String bucketName, String objectName);
void delete(String bucketName, String objectName);
```
- Object storage abstraction
- Implementations: MinIO, S3, Google Cloud Storage

### Authentication

**AuthPort**
```java
AuthDTO.AuthStatus getCurrentUserStatus();
```

## DTOs

### ItineraryDTO
- **Request**: Title, description, travel mode, steps, preferences
- **Response**: Full enriched representation with steps, AI suggestions, route geometry
- **StatusResponse**: Lightweight snapshot (id, status, queue position, progress %)
- **CreateResponse**: Minimal acknowledgment (id, token, status)
- **ItinerarySummary**: Summary for list views
- **ListResponse**: Paginated collection

### StepDTO
- **Request**: Place name, notes, arrival date
- **Response**: Full enriched step (geocoding, routing, POIs, AI description)

### PositionUpdateDTO
- **Request**: Step index, latitude, longitude
- **Response**: Confirmation with timestamp

## Domain Events

Located in `event/`:

### ItineraryCreatedEvent
Published when itinerary enqueued for processing.
```java
record ItineraryCreatedEvent(
    UUID id,
    String accessToken,
    String title,
    String travelMode,
    int stepCount,
    List<String> stepPlaceNames,
    String preferencesJson,
    String correlationId,
    Instant occurredAt
)
```

### ItineraryCompletedEvent
Published when all enrichment steps complete.
```java
record ItineraryCompletedEvent(
    UUID id,
    String title,
    double totalDistanceKm,
    int totalDurationMinutes,
    int stepCount,
    String correlationId,
    Instant occurredAt
)
```

### StepProcessedEvent
Published after each step's geocoding/routing/POI discovery.
```java
record StepProcessedEvent(
    UUID itineraryId,
    UUID stepId,
    int stepOrder,
    String placeName,
    Double latitude,
    Double longitude,
    Double distanceFromPrevKm,
    String correlationId,
    Instant occurredAt
)
```

### ItineraryFailedEvent
Published on unrecoverable errors.
```java
record ItineraryFailedEvent(
    UUID id,
    String title,
    String reason,
    String correlationId,
    Instant occurredAt
)
```

## Value Objects

Located in `vo/`:

- **GeoCoordinate**: Immutable coordinate pair (lat, lon)
- **GeoResult**: Geocoding result with address components
- **PoiResult**: Point of interest with metadata
- **RouteSegment**: Route geometry, distance, duration

## Dependencies

```xml
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

<!-- Lombok (code generation) -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <scope>provided</scope>
</dependency>
```

**No** Spring, JPA, or any framework dependencies.

## Hexagonal Architecture Patterns

### 1. Port-Based Design
- Inbound ports define **what the application does** (use cases)
- Outbound ports define **what the application needs** (SPIs)
- Both are pure Java interfaces

### 2. Dependency Inversion
```
Application Core (Business Logic)
    ↓ depends on
API Module (Ports - this module)
    ↑ implemented by
Adapters (REST, JPA, Redis, etc.)
```

### 3. Technology Agnostic
- No ORM entities (persistence uses generic `<T>`)
- No HTTP annotations (REST adapter interprets use cases)
- No messaging framework (event port is `void publish(Object)`)

### 4. Async Enrichment Pattern
1. Client calls `CreateItineraryUseCase.create()`
2. Skeleton persisted, access token generated
3. Job enqueued via `QueueManagementPort`
4. Background workers (geo, AI) process asynchronously
5. Client polls via `GetItineraryUseCase.getStatus()`

### 5. Access Token Security
- Itineraries are private; secured via bearer token (not JWT)
- Token required for all operations except create
- Token stored alongside aggregate ID in queue

## Usage by Other Modules

### Domain Implementations
- `itinerary-impl-core`: Business logic implementing use cases
- `itinerary-impl-jpa`: Persistence adapter implementing `ItineraryPersistencePort`
- `itinerary-impl-redis`: Cache and queue adapters
- `itinerary-impl-kafka`: Event publisher adapter
- `itinerary-impl-rest`: REST controller adapter

### Cross-Domain Dependencies
- `geo-api`: Depends on itinerary-api for value objects
- `geo-impl`: Uses `ItineraryStepDataPort` to update itinerary steps
- `ai-impl`: Uses `ItineraryStepDataPort` to enrich with AI content

### Application Assembly
- `app-monolith`: Composes all adapters in single JVM
- `app-microservice-itinerary`: Standalone microservice with selected adapters
