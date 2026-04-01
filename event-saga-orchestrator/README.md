# Event Saga Orchestrator

The **event-saga-orchestrator** is a stateless orchestration service that coordinates the multi-step itinerary enrichment workflow using the Saga pattern. It ensures reliable, fault-tolerant execution of the entire pipeline with automatic retry and compensation mechanisms.

## 🎯 Purpose

When a user creates a travel itinerary, it must be enriched through multiple processing stages:
1. **Geocoding** - Convert location names to coordinates
2. **Routing** - Calculate routes between destinations
3. **AI Enrichment** - Generate descriptions and travel tips
4. **POI Discovery** - Find points of interest near destinations

Each stage is handled by a dedicated worker service, and the saga orchestrator ensures they execute in the correct order with proper error handling.

## 🏗️ Architecture

### Saga Pattern Implementation

The orchestrator implements the **Saga pattern** for distributed transaction management:
- **Orchestration-based** (not choreography): central coordinator drives the workflow
- **Database as state store**: `SagaInstance` entities track workflow progress
- **Event-driven**: uses Kafka for asynchronous communication
- **Compensation**: automatic rollback on unrecoverable failures

### Components

```
┌─────────────────────────────────────────────────────────────┐
│              Event Saga Orchestrator                        │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │   KafkaEventListener (itinerary.created)             │  │
│  └─────────────────┬────────────────────────────────────┘  │
│                    │                                        │
│                    ▼                                        │
│  ┌──────────────────────────────────────────────────────┐  │
│  │      SagaOrchestrationService                        │  │
│  │  - start()                                           │  │
│  │  - onGeoCompleted() / onGeoFailed()                  │  │
│  │  - onRouteCompleted() / onRouteFailed()              │  │
│  │  - onAiCompleted() / onAiFailed()                    │  │
│  │  - onPoiCompleted() / onPoiFailed()                  │  │
│  │  - compensate()                                      │  │
│  └─────────┬────────────────────────────────────────────┘  │
│            │                                                │
│            ▼                                                │
│  ┌──────────────────────────────────────────────────────┐  │
│  │   SagaRepository (PostgreSQL)                        │  │
│  │   - SagaInstance (id, itineraryId, currentState,     │  │
│  │                   completedSteps, retryCount, etc.)  │  │
│  └─────────┬────────────────────────────────────────────┘  │
│            │                                                │
│            ▼                                                │
│  ┌──────────────────────────────────────────────────────┐  │
│  │   WorkerRequestPublisher (Kafka)                     │  │
│  │   - geo.worker.request                               │  │
│  │   - route.worker.request                             │  │
│  │   - ai.worker.request                                │  │
│  │   - poi.worker.request                               │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │   WorkerEventListener (Kafka)                        │  │
│  │   - geo.completed / geo.failed                       │  │
│  │   - route.completed / route.failed                   │  │
│  │   - ai.completed / ai.failed                         │  │
│  │   - poi.completed / poi.failed                       │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

## 🔄 Workflow

### Happy Path

```
1. Itinerary Service publishes: itinerary.created
   ↓
2. KafkaEventListener receives event → calls start()
   ↓
3. SagaInstance created with state: INITIAL → GEOCODING
   ↓
4. WorkerRequestPublisher publishes: geo.worker.request
   ↓
5. GeoWorker processes → publishes: geo.completed
   ↓
6. WorkerEventListener receives → calls onGeoCompleted()
   ↓
7. SagaInstance updated: GEOCODING → ROUTING
   ↓
8. WorkerRequestPublisher publishes: route.worker.request
   ↓
9. RouteWorker processes → publishes: route.completed
   ↓
10. SagaInstance updated: ROUTING → AI_ENRICHMENT
    ↓
11. AiWorker processes → publishes: ai.completed
    ↓
12. SagaInstance updated: AI_ENRICHMENT → POI_DISCOVERY
    ↓
13. PoiWorker processes → publishes: poi.completed
    ↓
14. SagaInstance updated: POI_DISCOVERY → COMPLETED ✅
```

### Failure and Retry Path

```
1. Worker publishes: geo.failed (with error message)
   ↓
2. WorkerEventListener receives → calls onGeoFailed()
   ↓
3. SagaOrchestrationService checks retry count:

   IF retryCount < maxRetries (3):
     - Increment retryCount
     - Calculate exponential backoff delay:
       delay = min(2000ms × 2^(retryCount-1), 30000ms)
       Example: 2s → 4s → 8s
     - Schedule retry using TaskScheduler
     - Republish: geo.worker.request

   ELSE (max retries exceeded):
     - Update SagaInstance state → FAILED
     - Update Itinerary status → FAILED (via JDBC)
     - Log compensation action ❌
```

## 📊 State Machine

The saga uses **Spring State Machine** to define valid state transitions:

### States

```java
public enum SagaStates {
    INITIAL,        // Saga created
    GEOCODING,      // Geo worker processing
    ROUTING,        // Route worker processing
    AI_ENRICHMENT,  // AI worker processing
    POI_DISCOVERY,  // POI worker processing
    COMPLETED,      // All steps successful ✅
    COMPENSATING,   // Executing compensation logic
    FAILED          // Saga failed after retries ❌
}
```

### Events

```java
public enum SagaEvents {
    START,                  // Trigger saga start
    GEOCODING_COMPLETED,    // Geo step succeeded
    GEOCODING_FAILED,       // Geo step failed
    ROUTING_COMPLETED,      // Route step succeeded
    ROUTING_FAILED,         // Route step failed
    AI_COMPLETED,           // AI step succeeded
    AI_FAILED,              // AI step failed
    POI_COMPLETED,          // POI step succeeded
    POI_FAILED,             // POI step failed
    COMPENSATION_COMPLETED  // Compensation finished
}
```

### State Transitions

```
INITIAL ──START──> GEOCODING
                   │
        ┌──────────┴──────────┐
        │                     │
 GEOCODING_COMPLETED   GEOCODING_FAILED
        │                     │
        ▼                     ▼
    ROUTING            COMPENSATING
        │                     │
  ┌─────┴─────┐        COMPENSATION_
  │           │         COMPLETED
ROUTING_   ROUTING_          │
COMPLETED   FAILED            ▼
  │           │            FAILED
  ▼           ▼
AI_ENRICHMENT  COMPENSATING
  │
  ├─────────────┐
  │             │
AI_COMPLETED  AI_FAILED
  │             │
  ▼             ▼
POI_DISCOVERY  COMPENSATING
  │
  ├─────────────┐
  │             │
POI_COMPLETED POI_FAILED
  │             │
  ▼             ▼
COMPLETED     FAILED
```

## 🔧 Key Features

### 1. Stateless Orchestration

The orchestrator service itself is **stateless**. All workflow state is persisted in the `saga_instances` table:

```sql
CREATE TABLE saga_instances (
    id               UUID PRIMARY KEY,
    itinerary_id     UUID NOT NULL,
    current_state    VARCHAR(50) NOT NULL,
    failed_step      VARCHAR(50),
    error_message    TEXT,
    version          BIGINT NOT NULL,  -- Optimistic locking
    retry_count      INT NOT NULL DEFAULT 0,
    max_retries      INT NOT NULL DEFAULT 3,
    preferences      TEXT,             -- JSON user preferences
    created_at       TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP NOT NULL
);

CREATE TABLE saga_completed_steps (
    saga_instance_id UUID NOT NULL,
    step             VARCHAR(50) NOT NULL,
    PRIMARY KEY (saga_instance_id, step),
    FOREIGN KEY (saga_instance_id) REFERENCES saga_instances(id)
);
```

**Benefits:**
- Service can be scaled horizontally
- State survives service restarts
- Audit trail of all saga executions

### 2. Idempotent Saga Creation

```java
@Transactional
public void start(UUID itineraryId, String preferencesJson) {
    List<SagaInstance> existing = sagaRepository.findByItineraryId(itineraryId);
    if (!existing.isEmpty()) {
        log.warn("Saga already exists for itinerary id={} – skipping", itineraryId);
        return;  // ✅ Idempotent: duplicate events are safe
    }
    // Create new saga...
}
```

Prevents duplicate saga creation if `itinerary.created` is redelivered by Kafka.

### 3. Automatic Retry with Exponential Backoff

```java
private void handleFailure(UUID itineraryId, String step, String errorMessage, Runnable retryAction) {
    SagaInstance saga = requireSaga(itineraryId);

    if (saga.canRetry()) {
        saga.incrementRetryCount();
        long delayMs = Math.min(
            (long) (RETRY_BASE_DELAY_MS * Math.pow(2, saga.getRetryCount() - 1)),
            RETRY_MAX_DELAY_MS
        );
        sagaRepository.save(saga);

        // Schedule retry
        taskScheduler.schedule(retryAction, Instant.now().plusMillis(delayMs));
    } else {
        // Max retries exceeded → compensate
        compensate(itineraryId, step, errorMessage);
    }
}
```

**Retry schedule:**
- Attempt 1: immediate
- Attempt 2: +2 seconds
- Attempt 3: +4 seconds
- Attempt 4: +8 seconds (but max 3 retries, so this won't happen)

### 4. Optimistic Locking

```java
@Entity
@Table(name = "saga_instances")
public class SagaInstance {

    @Version
    @Column(name = "version")
    private long version;  // JPA optimistic locking

    // ...
}
```

Prevents lost updates if two worker completion events arrive concurrently (highly unlikely but theoretically possible).

### 5. Compensation via JDBC

The saga orchestrator **does not own** the `Itinerary` JPA entity (that belongs to `itinerary-impl-core`). However, both services share the same PostgreSQL database.

On compensation, the saga updates the itinerary status via **raw JDBC**:

```java
@Transactional
public void compensate(UUID itineraryId, String failedStep, String reason) {
    // Update saga state
    saga.updateState("FAILED");
    sagaRepository.save(saga);

    // Update itinerary via JDBC (shared PostgreSQL DB)
    jdbcTemplate.update(
        "UPDATE itineraries SET status = 'FAILED', queue_position = NULL WHERE id = ?",
        itineraryId
    );
}
```

**Why JDBC?**
- Saga orchestrator runs as a separate Spring Boot application
- It cannot access `ItineraryService` or `ItineraryRepository` from `itinerary-impl-core`
- Both services connect to the same PostgreSQL instance
- JDBC is a simple, direct way to update the status atomically

### 6. User Preferences Forwarding

The saga stores user preferences (JSON blob) from the original `ItineraryCreatedEvent`:

```java
public void start(UUID itineraryId, String preferencesJson) {
    SagaInstance saga = SagaInstance.create(itineraryId, preferencesJson);
    // ...
}
```

These preferences are forwarded to each worker:

```java
workerRequestPublisher.requestAiProcessing(itineraryId, saga.getPreferences());
```

This allows workers to personalize processing (e.g., AI tone, route preferences, POI types).

## 🧪 Testing

The module includes comprehensive tests:

### Unit Tests

**`SagaOrchestrationServiceTest.java`**
- Tests saga start, completion handlers, failure handlers
- Mocks `SagaRepository`, `WorkerRequestPublisher`, `JdbcTemplate`, `TaskScheduler`
- Verifies correct state transitions and retry logic

### Integration Tests

**`SagaOrchestrationServiceIT.java`**
- Uses **Testcontainers** for PostgreSQL
- Verifies database interactions and JPA behavior
- Tests optimistic locking with `@Version`

**`SagaOrchestratorIT.java`**
- Full Spring Boot context
- Testcontainers for PostgreSQL + Kafka
- End-to-end saga workflow with real Kafka events

## 📂 Module Structure

```
event-saga-orchestrator/
├── src/main/java/com/travel/saga/
│   ├── SagaOrchestratorApplication.java       # Spring Boot main
│   ├── config/
│   │   └── SagaConfig.java                    # Spring beans (TaskScheduler, etc.)
│   ├── controller/
│   │   └── SagaAdminController.java           # REST API for saga monitoring
│   ├── domain/
│   │   ├── SagaInstance.java                  # JPA entity (saga state)
│   │   └── SagaStep.java                      # (unused - to be removed)
│   ├── repository/
│   │   └── SagaRepository.java                # Spring Data JPA
│   ├── service/
│   │   ├── SagaOrchestrationService.java      # Core orchestration logic ⭐
│   │   └── WorkerRequestPublisher.java        # Kafka producer
│   ├── listener/
│   │   ├── KafkaEventListener.java            # Listens to itinerary.created
│   │   ├── WorkerEventListener.java           # Listens to worker events ⭐
│   │   └── DlqListener.java                   # Dead letter queue
│   └── statemachine/
│       ├── ItinerarySagaConfig.java           # Spring State Machine config
│       ├── SagaStates.java                    # State enum
│       ├── SagaEvents.java                    # Event enum
│       └── SagaActions.java                   # State machine actions
├── src/main/resources/
│   ├── application.yml                        # Spring Boot config
│   └── db/migration/
│       └── V1__create_saga_tables.sql         # Flyway schema
└── src/test/java/
    └── com/travel/saga/
        ├── SagaOrchestrationServiceTest.java  # Unit tests
        ├── it/
        │   ├── SagaOrchestrationServiceIT.java
        │   └── SagaOrchestratorIT.java
```

## 🚀 Running the Service

### Standalone

```bash
# Start infrastructure (PostgreSQL, Kafka)
docker compose up -d

# Run the service
cd event-saga-orchestrator
mvn spring-boot:run
```

### With Full System

```bash
# Monolith deployment (saga orchestrator runs separately)
docker compose up -d
java -jar app-monolith/target/app-monolith-1.0.0-SNAPSHOT.jar
java -jar event-saga-orchestrator/target/event-saga-orchestrator-1.0.0-SNAPSHOT.jar

# Microservices deployment
docker compose -f docker-compose.prod.yml up -d
```

## 🔍 Monitoring

The saga orchestrator exposes REST endpoints for monitoring:

### Admin API

**`SagaAdminController.java`** (port 8084):

```bash
# Get saga instance by itinerary ID
GET /api/saga/itinerary/{itineraryId}

# List all sagas with pagination
GET /api/saga/all?page=0&size=20

# Get failed sagas
GET /api/saga/failed?page=0&size=20

# Manually retry a failed saga
POST /api/saga/{sagaId}/retry
```

### Logging

All saga events are logged with structured fields:

```
INFO  SagaOrchestration: started saga 123e4567-... for itinerary id=abc-def
INFO  SagaOrchestration: GEO completed for itinerary id=abc-def
WARN  SagaOrchestration: ROUTE failed for itinerary id=abc-def (attempt 1/3) – retrying in 2000ms
ERROR SagaOrchestration: AI failed after 3 retries for itinerary id=abc-def – compensating
```

Use log aggregation (ELK, Grafana Loki) to track saga health in production.

## 🔐 Security Considerations

1. **Kafka Consumer Groups**: Use dedicated consumer group `saga-orchestrator` to ensure exactly-once processing semantics
2. **Database Access**: Saga orchestrator requires `UPDATE` permission on `itineraries` table for compensation
3. **Idempotency**: Always check for existing sagas before creating new ones
4. **Dead Letter Queue**: Failed events should be routed to DLQ for manual inspection

## 🛠️ Configuration

**`application.yml`**:

```yaml
spring:
  application:
    name: event-saga-orchestrator

  datasource:
    url: jdbc:postgresql://localhost:5432/travel_db
    username: travel_user
    password: ${DB_PASSWORD}

  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: saga-orchestrator
      auto-offset-reset: earliest
      enable-auto-commit: false  # Manual commit for reliability

saga:
  retry:
    max-attempts: 3
    base-delay-ms: 2000
    max-delay-ms: 30000
```

## 📖 Further Reading

- [Saga Pattern Explained](https://microservices.io/patterns/data/saga.html)
- [Spring State Machine Docs](https://spring.io/projects/spring-statemachine)
- [Transactional Outbox Pattern](../event-messaging-starter/README.md)
- [Hexagonal Architecture](../README.md#-hexagonal-architecture-explained)

## 🤝 Contributing

When modifying the saga orchestrator:

1. **Add new steps**: Update `SagaStates`, `SagaEvents`, `ItinerarySagaConfig`, and `SagaOrchestrationService`
2. **Change retry logic**: Modify `RETRY_BASE_DELAY_MS`, `RETRY_MAX_DELAY_MS`, or `maxRetries` default
3. **Add monitoring**: Expose new metrics via `SagaAdminController`
4. **Update tests**: Ensure unit and integration tests cover new scenarios

Always maintain **idempotency** and **failure handling** when adding new saga steps.
