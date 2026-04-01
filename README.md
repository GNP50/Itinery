# Travel Itinerary Platform

A modular, cloud-native travel itinerary management system built with hexagonal architecture, enabling both monolithic and microservice deployments from the same codebase.

## 🏛️ Architecture

**Hexagonal Architecture (Ports & Adapters)**

The platform follows a strict hexagonal architecture pattern where:
- **API modules** (`*-api`) define technology-agnostic ports (contracts)
- **Core modules** (`*-impl-core`) implement business logic
- **Adapter modules** (`*-impl-*`) provide technology-specific implementations
- **Application modules** compose adapters for different deployment profiles

This design allows **infrastructure flexibility** through modular composition:
- Swap JPA for MongoDB by changing one Maven dependency
- Deploy as monolith or microservices without changing business code
- Test business logic without Docker containers

## 📦 Module Organization

The codebase is organized into layers following hexagonal architecture principles:

### 1. Domain APIs (Ports/Contracts)
Technology-agnostic interfaces defining what the application does and what it needs:
- [itinerary-api](itinerary-api/README.md) - Itinerary domain contracts (use cases, persistence ports, events)
- [geo-api](geo-api/README.md) - Geographic operations (geocoding, routing, POI discovery)
- [ai-api](ai-api/README.md) - AI enrichment (LLM integration, chat, suggestions)
- [user-api](user-api/README.md) - User management and authentication
- [saga-api](saga-api/README.md) - Saga orchestration contracts (commands, queries, persistence)
- [queue-api](queue-api/README.md) - Queue management contracts (FIFO with priority support)

### 2. Domain Implementations
- **Core modules** (`*-impl-core`) - Pure business logic implementing use cases
- **Inbound adapters** (`*-impl-rest`, `*-impl-grpc-server`, `*-impl-kafka`) - Controllers and event consumers
- **Outbound adapters** (`*-impl-jpa`, `*-impl-redis`, `*-impl-http`, `*-impl-ollama`) - Infrastructure implementations

📖 **See [Implementation Modules Documentation](docs/IMPLEMENTATION_MODULES.md)** for complete module descriptions.

### 3. Infrastructure Services
- [event-saga-orchestrator](event-saga-orchestrator/README.md) - Coordinates multi-step itinerary enrichment workflow
- [config-manager](config-manager/) - Centralized configuration service
- [file-manager](file-manager/) - File upload/download service
- [monitoring-service](monitoring-service/) - Observability and metrics

### 4. Shared Starters
- [config-client-starter](config-client-starter/README.md) - Configuration client library (gRPC)
- [event-messaging-starter](event-messaging-starter/README.md) - Transactional outbox pattern implementation

### 5. Deployable Applications
- `app-monolith` - All-in-one deployment (single JVM)
- `app-microservice-itinerary` - Itinerary domain microservice
- `app-microservice-ai` - AI enrichment microservice
- `app-microservice-user` - User management microservice

### 6. Frontend
- `frontend/` - React + TypeScript SPA with Leaflet maps and TailwindCSS

## 🚀 Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 21+
- Maven 3.9+

### 1. Start Infrastructure

```bash
docker compose up -d
```

This starts:
- PostgreSQL + PostGIS (itinerary data)
- Redis (cache + queue)
- Apache Kafka (events)
- MinIO (file storage)
- Ollama (local LLM for AI enrichment)

### 2. Build

```bash
mvn clean package -DskipTests
```

### 3. Run Monolith

```bash
java -jar app-monolith/target/app-monolith-1.0.0-SNAPSHOT.jar
```

### 4. Access API

- Swagger UI: http://localhost:8080/swagger-ui.html
- API Docs: http://localhost:8080/v3/api-docs

### 5. Build & Run Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend: http://localhost:3000

## 📋 Features

### Core Functionality
- ✅ Create and manage travel itineraries
- ✅ Automatic geocoding of destinations
- ✅ Turn-by-turn routing between locations
- ✅ POI discovery via OpenStreetMap
- ✅ AI-powered descriptions and travel tips
- ✅ Real-time position tracking
- ✅ Interactive maps with Leaflet

### Technical Features
- ✅ Hexagonal architecture with pluggable adapters
- ✅ Event-driven processing with Kafka
- ✅ Transactional outbox pattern for reliability
- ✅ gRPC for internal microservice communication
- ✅ Redis for caching and queue management
- ✅ Circuit breakers and resilience patterns
- ✅ Distributed tracing via X-Correlation-Id
- ✅ Micrometer metrics integration

## 🏗️ Hexagonal Architecture Explained

### Port Types

**Inbound Ports (Use Cases)**
```java
public interface CreateItineraryUseCase {
    CreateResponse create(Request request);
}
```
- Define what the application **does**
- Technology-agnostic interfaces
- Located in `*-api` modules

**Outbound Ports (SPIs)**
```java
public interface ItineraryPersistencePort<T> {
    T save(T itinerary);
    Optional<T> findById(UUID id);
}
```
- Define what the application **needs**
- Abstract infrastructure concerns
- Located in `*-api` modules

### Adapter Implementations

**Inbound Adapters** (drive the application):
- REST controllers (`*-impl-rest`)
- gRPC servers (`*-impl-grpc-server`)
- Kafka consumers (`*-impl-kafka`)
- CLI interfaces (future)

**Outbound Adapters** (driven by the application):
- JPA persistence (`*-impl-jpa`)
- Redis cache/queue (`*-impl-redis`)
- HTTP clients (`*-impl-http`, `ai-impl-ollama`)
- Kafka publishers (`*-impl-kafka`)
- File storage (`*-impl-storage`)

### Composition Examples

**Monolith POM:**
```xml
<dependencies>
    <!-- Core business logic -->
    <dependency>
        <groupId>com.travel</groupId>
        <artifactId>itinerary-impl-core</artifactId>
    </dependency>

    <!-- Adapters selected for monolith -->
    <dependency>
        <groupId>com.travel</groupId>
        <artifactId>itinerary-impl-rest</artifactId>
    </dependency>
    <dependency>
        <groupId>com.travel</groupId>
        <artifactId>itinerary-impl-jpa</artifactId>
    </dependency>
    <!-- All domains co-located, no gRPC clients needed -->
</dependencies>
```

**Microservice POM:**
```xml
<dependencies>
    <!-- Same core business logic -->
    <dependency>
        <groupId>com.travel</groupId>
        <artifactId>itinerary-impl-core</artifactId>
    </dependency>

    <!-- Different adapters for microservice -->
    <dependency>
        <groupId>com.travel</groupId>
        <artifactId>itinerary-impl-rest</artifactId>
    </dependency>
    <dependency>
        <groupId>com.travel</groupId>
        <artifactId>itinerary-impl-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>com.travel</groupId>
        <artifactId>ai-grpc-client</artifactId>
    </dependency>
    <dependency>
        <groupId>com.travel</groupId>
        <artifactId>user-grpc-client</artifactId>
    </dependency>
    <!-- Remote calls via gRPC -->
</dependencies>
```

**Key Insight**: Same business logic, different adapter composition → different deployment profile.

## 📐 Architecture Diagrams

### Hexagonal Architecture Layers

```
┌─────────────────────────────────────────────────────────┐
│                   Inbound Adapters                      │
│  (REST Controllers, gRPC Servers, Kafka Consumers)      │
└─────────────────┬───────────────────────────────────────┘
                  │ implements
                  ▼
┌─────────────────────────────────────────────────────────┐
│                  Inbound Ports                          │
│        (Use Cases: CreateItineraryUseCase, etc.)        │
└─────────────────┬───────────────────────────────────────┘
                  │ defined in *-api
                  ▼
┌─────────────────────────────────────────────────────────┐
│              Domain Core (*-impl-core)                   │
│        (Business Logic, Domain Entities, Services)      │
└─────────────────┬───────────────────────────────────────┘
                  │ depends on
                  ▼
┌─────────────────────────────────────────────────────────┐
│                 Outbound Ports                          │
│  (SPIs: PersistencePort, EventPublisherPort, etc.)     │
└─────────────────┬───────────────────────────────────────┘
                  │ implemented by
                  ▼
┌─────────────────────────────────────────────────────────┐
│                  Outbound Adapters                      │
│      (JPA, Redis, Kafka, HTTP Clients, MinIO)          │
└─────────────────────────────────────────────────────────┘
```

### Monolith Deployment

```
┌─────────────────────────────────────────────────────────┐
│                    app-monolith                         │
│                                                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌─────────┐│
│  │Itinerary │  │   Geo    │  │    AI    │  │  User   ││
│  │  Domain  │──│  Domain  │──│  Domain  │──│ Domain  ││
│  └──────────┘  └──────────┘  └──────────┘  └─────────┘│
│       │             │              │             │      │
│       └─────────────┴──────────────┴─────────────┘      │
│                     │                                   │
│              Shared Persistence                         │
└─────────────────────┬───────────────────────────────────┘
                      │
         ┌────────────┴─────────────┐
         │                          │
    PostgreSQL                   Redis
```

### Microservice Deployment

```
┌──────────────────┐    gRPC     ┌──────────────────┐
│   app-microservice│◄──────────►│app-microservice  │
│    -itinerary    │             │      -ai         │
└────────┬─────────┘             └──────────────────┘
         │                              ▲
         │ gRPC                         │
         ▼                              │
┌──────────────────┐                   │
│ app-microservice │                   │
│      -user       │───────────────────┘
└────────┬─────────┘
         │
         │  Kafka Events
         ▼
┌─────────────────────────────────────────┐
│          Apache Kafka                   │
│  (ItineraryCreatedEvent,               │
│   ItineraryCompletedEvent, etc.)       │
└─────────────────────────────────────────┘
```

Each microservice:
- Independent database
- Independent deployment
- gRPC for synchronous calls
- Kafka for asynchronous events

## 🔄 Event-Driven Processing

### Asynchronous Enrichment Pipeline

```
1. Client creates itinerary → ItineraryCreatedEvent
   ↓
2. GeoWorker geocodes all steps → lat/lon, address
   ↓
3. RouteWorker calculates routes → distance, duration, geometry
   ↓
4. PoiWorker discovers attractions → POI list
   ↓
5. AiWorker enriches with AI → descriptions, tips
   ↓
6. ItineraryCompletedEvent published → Client polls status
```

### Saga Orchestrator

The **[event-saga-orchestrator](event-saga-orchestrator/README.md)** service coordinates the multi-step itinerary enrichment process using the Saga pattern. It ensures reliable execution of the entire workflow with automatic retry and compensation mechanisms.

**Workflow:**
1. Listens for `itinerary.created` events from Kafka
2. Creates a `SagaInstance` to track workflow state in PostgreSQL
3. Orchestrates sequential execution: GEOCODING → ROUTING → AI_ENRICHMENT → POI_DISCOVERY → COMPLETED
4. Publishes worker request events and receives completion/failure events
5. Retries failed steps with exponential backoff (max 3 retries, 2s-30s delays)
6. Compensates by marking both saga and itinerary as FAILED after max retries

**Key features:**
- ✅ Stateless orchestration (state persisted in database)
- ✅ Automatic retry with exponential backoff
- ✅ Idempotent saga creation (prevents duplicate processing)
- ✅ Optimistic locking via JPA `@Version` for concurrent updates
- ✅ Event-driven communication via Kafka

📖 **[Read full Saga documentation →](event-saga-orchestrator/README.md)**

### Transactional Outbox Pattern

Guarantees at-least-once event delivery:

```
@Transactional
public void createItinerary(Request request) {
    repository.save(itinerary);           // DB write
    outboxRepository.save(outboxEvent);   // DB write (same tx)
}
// Background poller sends events to Kafka asynchronously
```

**Guarantees**:
- No dual-write problem
- Events survive application crashes
- Atomic consistency between state and events

## 🧪 Testing

### Unit Tests (Core Modules)
```bash
mvn test -pl itinerary-impl-core
```
- Pure business logic
- Mocked ports
- No infrastructure dependencies

### Integration Tests (Adapters)
```bash
mvn verify -pl itinerary-impl-jpa
```
- Testcontainers for PostgreSQL, Redis, Kafka
- Real infrastructure
- Port contract verification

### End-to-End Tests (Applications)
```bash
mvn verify -pl app-monolith
```
- Full Spring Boot context
- Docker Compose infrastructure
- API-level testing

## 🐳 Docker Deployment

### Build Images
```bash
mvn clean package -Pdocker
```

### Docker Compose
```bash
docker compose -f docker-compose.prod.yml up -d
```

Services:
- `itinerary-service` (port 8080)
- `ai-service` (port 8081)
- `user-service` (port 8082)
- `postgres`, `redis`, `kafka`, `minio`, `ollama`

## 🛠️ Technology Stack

### Backend
- **Language**: Java 21 (Records, Pattern Matching, Virtual Threads)
- **Framework**: Spring Boot 3.4
- **Persistence**: Spring Data JPA + Hibernate + PostGIS
- **Caching**: Redis (Lettuce)
- **Messaging**: Apache Kafka
- **gRPC**: gRPC Java + Protocol Buffers
- **Object Storage**: MinIO
- **AI**: Ollama (local LLM)
- **Database**: PostgreSQL 16 + PostGIS
- **Build**: Maven 3.9

### Frontend
- **Framework**: React 18 + TypeScript
- **Maps**: Leaflet + React-Leaflet
- **Styling**: TailwindCSS
- **State**: Zustand
- **Build**: Vite

### Infrastructure
- **Containerization**: Docker + Docker Compose
- **Observability**: Micrometer + Prometheus + Grafana
- **Tracing**: OpenTelemetry
- **API Docs**: SpringDoc OpenAPI (Swagger)

## 🗂️ External Services

| Service | Purpose | Rate Limit | Cost |
|---------|---------|------------|------|
| Nominatim | Geocoding | 1 req/sec | Free |
| OSRM | Routing | None | Free |
| Overpass API | POI Discovery | None | Free |
| Ollama | AI Enrichment | None | Free (local) |

All services have fallback mechanisms and circuit breakers.

## 📚 Documentation

### Architecture Documentation
- [Implementation Modules](docs/IMPLEMENTATION_MODULES.md) - All implementation modules overview with hexagonal architecture layers
- [Saga Orchestrator](event-saga-orchestrator/README.md) - Event-driven saga pattern for itinerary enrichment workflow

### Domain API Documentation
- [Itinerary API](itinerary-api/README.md) - Itinerary domain contracts (ports, DTOs, events)
- [Geo API](geo-api/README.md) - Geographic operations contracts (geocoding, routing, POI)
- [AI API](ai-api/README.md) - AI enrichment contracts (LLM integration)
- [User API](user-api/README.md) - User management contracts
- [Saga API](saga-api/README.md) - Saga orchestration contracts (commands, queries, lifecycle)
- [Queue API](queue-api/README.md) - Queue management contracts (FIFO with priority)

### Infrastructure Documentation
- [Config Client Starter](config-client-starter/README.md) - Centralized configuration management
- [Event Messaging Starter](event-messaging-starter/README.md) - Transactional outbox pattern for reliable event publishing

## 🚀 Development Workflow

### Add New Feature
1. Define ports in `*-api` module
2. Implement business logic in `*-impl-core`
3. Create adapters in `*-impl-*` modules
4. Compose in application modules
5. Test in isolation (unit) and integration (adapters)

### Swap Technology
1. Create new adapter module (e.g., `itinerary-impl-mongodb`)
2. Implement existing port contracts
3. Update application POM dependency
4. No business logic changes required

### Deploy as Microservice
1. Extract domain to `app-microservice-*`
2. Add gRPC server adapter
3. Update other services to use gRPC client
4. Configure Kafka for events

## 🤝 Contributing

1. Follow hexagonal architecture principles
2. Keep API modules technology-agnostic
3. Implement ports in separate adapter modules
4. Write tests for both core logic and adapters
5. Update documentation for new modules

## 📄 License

MIT License - see LICENSE file

## 👥 Team

Built with hexagonal architecture principles for maintainability, testability, and deployment flexibility.
