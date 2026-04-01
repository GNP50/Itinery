# Implementation Modules Documentation

This document provides an overview of all implementation modules in the Travel Itinerary Platform, organized by their role in the hexagonal architecture.

## Module Organization

### Domain API Modules (Ports/Contracts)
- [`itinerary-api`](../itinerary-api/README.md) - Itinerary domain contracts
- [`geo-api`](../geo-api/README.md) - Geographic operations contracts
- [`ai-api`](../ai-api/README.md) - AI enrichment contracts
- [`user-api`](../user-api/README.md) - User management contracts
- [`saga-api`](../saga-api/README.md) - Saga orchestration contracts
- [`queue-api`](../queue-api/README.md) - Queue management contracts

### Domain Core Modules (Business Logic)
- `itinerary-impl-core` - Core itinerary business logic
- `geo-impl-core` - Geographic processing services
- `ai-impl-core` - AI enrichment services
- `user-impl-core` - User management services
- `saga-impl-core` - Saga orchestration business logic

### Inbound Adapters (Primary Adapters)
- `itinerary-impl-rest` - REST HTTP controllers
- `user-impl-rest` - User REST HTTP controllers
- `ai-impl-grpc-server` - AI gRPC server
- `user-impl-grpc-server` - User gRPC server
- `saga-impl-grpc-server` - Saga gRPC server
- `itinerary-impl-kafka` - Kafka event consumers (itinerary domain)
- `geo-impl-kafka` - Kafka event consumers (geo workers)
- `ai-impl-kafka` - Kafka event consumers (AI workers)

### Outbound Adapters (Secondary Adapters)
- `itinerary-impl-jpa` - JPA persistence for itineraries
- `user-impl-jpa` - JPA persistence for users
- `saga-impl-jpa` - JPA persistence for saga instances
- `itinerary-impl-redis` - Redis cache and queue
- `queue-impl-redis` - Redis queue implementation
- `itinerary-impl-storage` - MinIO file storage
- `itinerary-impl-kafka` - Kafka event publishing
- `geo-impl-http` - HTTP clients for external geo services
- `ai-impl-ollama` - HTTP client for Ollama LLM

### Aggregator Modules
- `itinerary-impl` - Aggregates all itinerary adapters
- `geo-impl` - Aggregates all geo adapters
- `ai-impl` - Aggregates all AI adapters
- `user-impl` - Aggregates all user adapters

### gRPC Clients (Cross-Domain Communication)
- `ai-grpc-client` - Client stubs for AI service
- `user-grpc-client` - Client stubs for User service
- `geo-grpc-client` - Client stubs for Geo service
- `saga-grpc-client` - Client stubs for Saga service

### Shared DTOs
- `saga-dto` - Shared saga data transfer objects

### Shared Infrastructure
- [`config-client-starter`](../config-client-starter/README.md) - Configuration client library
- [`event-messaging-starter`](../event-messaging-starter/README.md) - Transactional outbox pattern

### Infrastructure Services
- `config-manager` - Centralized configuration service
- [`event-saga-orchestrator`](../event-saga-orchestrator/README.md) - Saga pattern coordinator
- `file-manager` - File operations service
- `monitoring-service` - Observability and metrics

### Application Modules
- `app-monolith` - All-in-one deployment
- `app-microservice-itinerary` - Itinerary microservice
- `app-microservice-ai` - AI microservice
- `app-microservice-user` - User microservice

## Domain Core Modules

### itinerary-impl-core

**Purpose**: Pure business logic for itinerary management

**Key Components**:
- `ItineraryService` - Implements all use cases from `itinerary-api`
- `ItineraryProcessor` - Orchestrates enrichment workflow
- `JwtTokenService` - Access token generation and validation

**Dependencies**:
- `itinerary-api` (implements ports)
- `ai-api`, `geo-api` (cross-domain ports)
- Minimal Spring Boot (no web, persistence, or messaging)

### geo-impl-core

**Purpose**: Geographic processing business logic

**Key Components**:
- `GeocodingService` - Implements `GeoWorkerUseCase`
- `RoutingService` - Implements `RouteWorkerUseCase`
- `PoiDiscoveryService` - Implements `PoiWorkerUseCase`

**Dependencies**:
- `geo-api` (implements worker ports)
- `itinerary-api` (for value objects)

### ai-impl-core

**Purpose**: AI enrichment business logic

**Key Components**:
- `AiService` - Implements `AiWorkerUseCase`
- Prompt engineering utilities

**Dependencies**:
- `ai-api` (implements worker port)

### user-impl-core

**Purpose**: User management business logic

**Key Components**:
- `UserService` - Implements `UserUseCase`
- Password hashing utilities

**Dependencies**:
- `user-api` (implements use case)

### saga-impl-core

**Purpose**: Saga orchestration business logic

**Key Components**:
- `SagaCommandService` - Implements `SagaCommandPort` (retry, compensate operations)
- `SagaQueryService` - Implements `SagaQueryPort` (query saga instances)
- `SagaOrchestrationService` - Coordinates worker execution via events

**Dependencies**:
- `saga-api` (implements ports)
- `itinerary-api` (for cross-domain events)

**Note**: This is used by `event-saga-orchestrator` service.

## Inbound Adapters

### itinerary-impl-rest

**Purpose**: REST HTTP API for itinerary operations

**Components**:
- `ItineraryRestController` - CRUD operations
- `QueueRestController` - Queue status
- `GeoRestController` - Geographic operations (delegates)
- `AiRestController` - AI operations (delegates)

**Technology**: Spring Web + SpringDoc OpenAPI

### user-impl-rest

**Purpose**: REST HTTP API for user management

**Components**:
- `UserRestController` - User CRUD operations
- `AuthController` - Authentication endpoints

**Technology**: Spring Web + Spring Security

### *-impl-grpc-server

**Purpose**: gRPC service implementations for microservice communication

**Modules**:
- `ai-impl-grpc-server` - AI service gRPC endpoints
- `user-impl-grpc-server` - User service gRPC endpoints
- `saga-impl-grpc-server` - Saga service gRPC endpoints

**Technology**: gRPC + Protocol Buffers

### *-impl-kafka (Consumers)

**Purpose**: Event-driven processing via Kafka consumers

**Modules**:
- `itinerary-impl-kafka` - Listens for itinerary domain events
- `geo-impl-kafka` - Listens for geo worker requests
- `ai-impl-kafka` - Listens for AI worker requests

**Technology**: Spring Kafka

## Outbound Adapters

### itinerary-impl-jpa

**Purpose**: PostgreSQL persistence for itineraries

**Components**:
- `ItineraryPersistenceAdapter` - Implements `ItineraryPersistencePort<T>`
- `ItineraryJpaRepository` - Spring Data JPA
- `ItineraryEntity`, `StepEntity` - JPA entities

**Technology**: Spring Data JPA + Hibernate + PostGIS

### user-impl-jpa

**Purpose**: PostgreSQL persistence for users

**Components**:
- `UserPersistenceAdapter` - Implements `UserPersistencePort<T>`
- `UserJpaRepository` - Spring Data JPA
- `UserEntity` - JPA entity

**Technology**: Spring Data JPA + Hibernate

### saga-impl-jpa

**Purpose**: PostgreSQL persistence for saga instances

**Components**:
- `SagaPersistenceAdapter` - Implements `SagaPersistencePort<T>`
- `SagaJpaRepository` - Spring Data JPA
- `SagaInstanceEntity`, `SagaStepEntity` - JPA entities

**Technology**: Spring Data JPA + Hibernate + Optimistic Locking (`@Version`)

**Note**: Used by `event-saga-orchestrator` service to persist saga state.

### itinerary-impl-redis

**Purpose**: Caching and queue management for itinerary domain

**Components**:
- `RedisCacheAdapter` - Implements `CachePort`
- `RedisQueueAdapter` - Implements `QueuePort`
- `QueueManagementPortAdapter` - Implements `QueueManagementPort`

**Technology**: Spring Data Redis + Lettuce

### queue-impl-redis

**Purpose**: Generic Redis queue implementation

**Components**:
- `RedisQueueAdapter` - Implements `QueuePort<T>`
- `QueueManagementPortAdapter` - Implements `QueueManagementPort`
- Uses Redis Sorted Set (ZSET) for priority-based FIFO

**Technology**: Spring Data Redis + Lettuce

**Note**: Cross-domain infrastructure used by multiple domains.

### itinerary-impl-storage

**Purpose**: File object storage

**Components**:
- `MinioStorageAdapter` - Implements `FileStoragePort`

**Technology**: MinIO Java SDK

### itinerary-impl-kafka (Publisher)

**Purpose**: Event publishing via transactional outbox

**Components**:
- `OutboxEventPublisherAdapter` - Implements `EventPublisherPort`
- Uses `event-messaging-starter` for outbox pattern

**Technology**: Spring Kafka + Transactional Outbox

### geo-impl-http

**Purpose**: External geographic service integrations

**Components**:
- `PhotonAdapter` - Photon geocoding (implements `GeocodingPort`)
- `NominatimAdapter` - Nominatim geocoding (implements `GeocodingPort`)
- `OsrmAdapter` - OSRM routing (implements `RoutingPort`)
- `OverpassAdapter` - Overpass POI discovery (implements `PoiDiscoveryPort`)

**Technology**: Spring WebClient + Resilience4j

### ai-impl-ollama

**Purpose**: Local LLM integration

**Components**:
- `OllamaHttpAdapter` - Implements `AiEnrichmentPort` and `AiChatPort`

**Technology**: HTTP client + JSON

## Aggregator Modules

These modules aggregate multiple adapter modules for convenience in deployment configuration.

### itinerary-impl

**Purpose**: Aggregates all itinerary domain adapters

**Aggregated modules**:
- `itinerary-impl-core`
- `itinerary-impl-rest`
- `itinerary-impl-jpa`
- `itinerary-impl-redis`
- `itinerary-impl-storage`
- `itinerary-impl-kafka`

### geo-impl

**Purpose**: Aggregates all geo domain adapters

**Aggregated modules**:
- `geo-impl-core`
- `geo-impl-http`
- `geo-impl-kafka`

### ai-impl

**Purpose**: Aggregates all AI domain adapters

**Aggregated modules**:
- `ai-impl-core`
- `ai-impl-ollama`
- `ai-impl-kafka`
- `ai-impl-grpc-server`

### user-impl

**Purpose**: Aggregates all user domain adapters

**Aggregated modules**:
- `user-impl-core`
- `user-impl-rest`
- `user-impl-jpa`
- `user-impl-grpc-server`

**Benefits of aggregator modules**:
- Simplified dependency management
- Consistent adapter composition
- Single version for all related adapters
- Easier to maintain deployment configurations

## Adapter Composition Patterns

### Monolith Assembly
```
app-monolith/
├── Inbound: itinerary-impl-rest
├── Core: *-impl-core (all domains)
├── Outbound:
│   ├── itinerary-impl-jpa
│   ├── itinerary-impl-redis
│   ├── itinerary-impl-storage
│   ├── geo-impl-http
│   └── ai-impl-ollama
└── No gRPC clients (in-process calls)
```

### Microservice Assembly
```
app-microservice-itinerary/
├── Inbound: itinerary-impl-rest
├── Core: itinerary-impl-core
├── Outbound:
│   ├── itinerary-impl-jpa
│   ├── itinerary-impl-redis
│   ├── itinerary-impl-storage
│   ├── itinerary-impl-kafka
│   ├── ai-grpc-client → ai microservice
│   ├── user-grpc-client → user microservice
│   └── geo-grpc-client → geo microservice
└── Kafka for events
```

## Hexagonal Architecture Realization

### Dependency Flow

```
REST Controller (itinerary-impl-rest)
    ↓ calls
Use Case Port (itinerary-api)
    ↓ implemented by
Service (itinerary-impl-core)
    ↓ depends on
Outbound Port (itinerary-api)
    ↓ implemented by
Adapter (itinerary-impl-jpa)
```

### Key Principles

1. **Core depends on API, not adapters**
2. **Adapters implement ports defined in API**
3. **Applications compose adapters via Maven**
4. **Same business logic, different deployment profiles**

## Module Selection Matrix

| Module | Monolith | Itinerary µS | AI µS | User µS | Saga Orchestrator |
|--------|----------|-------------|-------|---------|-------------------|
| itinerary-impl-core | ✓ | ✓ | - | - | - |
| geo-impl-core | ✓ | - | - | - | - |
| ai-impl-core | ✓ | - | ✓ | - | - |
| user-impl-core | ✓ | - | - | ✓ | - |
| saga-impl-core | - | - | - | - | ✓ |
| itinerary-impl-rest | ✓ | ✓ | - | - | - |
| user-impl-rest | ✓ | - | - | ✓ | - |
| itinerary-impl-jpa | ✓ | ✓ | - | - | - |
| user-impl-jpa | ✓ | - | - | ✓ | - |
| saga-impl-jpa | - | - | - | - | ✓ |
| itinerary-impl-redis | ✓ | ✓ | - | - | - |
| queue-impl-redis | ✓ | ✓ | - | - | ✓ |
| geo-impl-http | ✓ | - | - | - | - |
| ai-impl-ollama | ✓ | - | ✓ | - | - |
| ai-grpc-client | - | ✓ | - | - | - |
| user-grpc-client | - | ✓ | - | - | - |
| saga-grpc-client | - | ✓ | - | - | - |
| ai-impl-grpc-server | - | - | ✓ | - | - |
| user-impl-grpc-server | - | - | - | ✓ | - |
| saga-impl-grpc-server | - | - | - | - | ✓ |
| geo-impl-kafka | ✓ | - | - | - | - |
| ai-impl-kafka | ✓ | - | ✓ | - | - |
| itinerary-impl-kafka | ✓ | ✓ | - | - | - |

**Legend:**
- ✓ = Module included in this deployment
- µS = Microservice
- Monolith = All domains co-located in single JVM
- Saga Orchestrator = Standalone service for workflow coordination

## Technology Stack by Layer

### Core Layer
- Java 21
- Spring Framework (DI, AOP, Validation)
- No infrastructure dependencies

### Inbound Layer
- Spring Web MVC (REST)
- gRPC + Protobuf (µservices)
- Spring Kafka (event consumers)

### Outbound Layer
- Spring Data JPA + Hibernate (persistence)
- Spring Data Redis + Lettuce (cache/queue)
- MinIO SDK (storage)
- Spring Kafka (event publishing)
- WebClient + Resilience4j (external HTTP)

## Testing Strategy

### Core Modules
- Pure unit tests (no Spring context)
- Mock outbound ports
- Test business logic in isolation

### Adapters
- Integration tests with Testcontainers
- Test port implementations against real infrastructure
- Use test slices (`@DataJpaTest`, `@WebMvcTest`)

### Applications
- End-to-end tests
- Full Spring Boot context
- Docker Compose for infrastructure
