# Saga API

## Overview

Domain API module defining contracts for saga orchestration in the hexagonal architecture. Provides ports for managing saga instances, querying saga state, and coordinating distributed transactions across multiple microservices.

## Purpose

Defines the contracts for:
- Saga instance lifecycle management (start, retry, compensate)
- Saga state queries and monitoring
- Saga persistence abstraction
- Event publishing for saga coordination

## Architecture Role

**Hexagonal Layer**: Ports (Contracts)
- **Inbound Ports**: Command and query interfaces for saga operations
- **Outbound Ports**: SPIs for saga persistence and event publishing

## Module Structure

```
saga-api/
├── port/
│   ├── inbound/
│   │   ├── SagaCommandPort.java      # Saga commands (retry, compensate)
│   │   └── SagaQueryPort.java        # Saga queries (get, list, history)
│   └── outbound/
│       ├── SagaPersistencePort.java  # Saga state persistence
│       └── SagaEventPublisherPort.java # Event publishing
└── dto/
    ├── SagaInstanceDto.java          # Saga instance representation
    ├── SagaOperationResult.java      # Command result
    ├── SagaListRequest.java          # Query filters
    └── SagaStateTransitionDto.java   # State transition history
```

## Inbound Ports

Located in `port/inbound/`:

### SagaCommandPort
```java
public interface SagaCommandPort {
    /**
     * Retry a failed saga instance
     */
    SagaOperationResult retrySaga(UUID sagaId);

    /**
     * Trigger compensation (rollback) for a saga instance
     */
    SagaOperationResult compensateSaga(UUID sagaId);
}
```

**Operations:**
- `retrySaga()` - Manually retry a failed saga from the last failed step
- `compensateSaga()` - Trigger compensation logic to rollback changes

### SagaQueryPort
```java
public interface SagaQueryPort {
    /**
     * Get a saga instance by its ID
     */
    Optional<SagaInstanceDto> getSagaInstance(UUID sagaId);

    /**
     * Get saga instances by itinerary ID
     */
    List<SagaInstanceDto> getSagasByItinerary(UUID itineraryId);

    /**
     * List saga instances with filters and pagination
     */
    List<SagaInstanceDto> listSagaInstances(SagaListRequest request);

    /**
     * Count total saga instances matching the filter
     */
    long countSagaInstances(SagaListRequest request);

    /**
     * Get the state transition history for a saga instance
     */
    List<SagaStateTransitionDto> getSagaHistory(UUID sagaId);
}
```

**Query capabilities:**
- Single saga lookup by ID
- Find all sagas for an itinerary
- List with filtering (by state, date range, itinerary)
- Pagination support
- State transition audit trail

## Outbound Ports (SPIs)

### SagaPersistencePort
```java
public interface SagaPersistencePort<T> {
    T save(T sagaInstance);
    Optional<T> findById(UUID id);
    List<T> findByItineraryId(UUID itineraryId);
    List<T> findByState(String state);
    // ... additional query methods
}
```

**Purpose**: Abstract saga state persistence
**Implementations:**
- `saga-impl-jpa` - PostgreSQL persistence with JPA

### SagaEventPublisherPort
```java
public interface SagaEventPublisherPort {
    void publishSagaStarted(UUID sagaId, UUID itineraryId);
    void publishSagaCompleted(UUID sagaId, UUID itineraryId);
    void publishSagaFailed(UUID sagaId, UUID itineraryId, String reason);
    void publishSagaCompensated(UUID sagaId, UUID itineraryId);
}
```

**Purpose**: Publish saga lifecycle events to Kafka
**Implementations:**
- Kafka producer in `event-saga-orchestrator`

## DTOs

### SagaInstanceDto
```java
public record SagaInstanceDto(
    UUID id,
    UUID itineraryId,
    String currentState,           // INITIAL, GEOCODING, ROUTING, etc.
    List<String> completedSteps,
    String failedStep,
    String errorMessage,
    int retryCount,
    int maxRetries,
    String preferences,            // JSON user preferences
    Instant createdAt,
    Instant updatedAt
) {}
```

### SagaOperationResult
```java
public record SagaOperationResult(
    boolean success,
    String message,
    UUID sagaId
) {}
```

### SagaListRequest
```java
public record SagaListRequest(
    String state,                  // Filter by state
    UUID itineraryId,              // Filter by itinerary
    int page,
    int size
) {}
```

### SagaStateTransitionDto
```java
public record SagaStateTransitionDto(
    UUID sagaId,
    String fromState,
    String toState,
    String eventType,              // STEP_COMPLETED, STEP_FAILED, etc.
    Instant occurredAt
) {}
```

## Dependencies

```xml
<!-- Self-contained API module -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>

<dependency>
    <groupId>jakarta.validation</groupId>
    <artifactId>jakarta.validation-api</artifactId>
</dependency>
```

**No** dependency on itinerary-api or other domain APIs.

## Hexagonal Architecture Patterns

### 1. Saga Pattern Implementation

This API enables the **Saga pattern** for distributed transactions:

```
1. Client creates itinerary
   ↓
2. SagaOrchestrator starts saga instance
   ↓
3. Saga coordinates workers: GEO → ROUTING → AI → POI
   ↓
4. On failure: Automatic retry OR compensation
   ↓
5. On success: Saga marked COMPLETED
```

### 2. Command-Query Separation (CQS)

- **Commands** (`SagaCommandPort`) - State-changing operations (retry, compensate)
- **Queries** (`SagaQueryPort`) - Read-only operations (get, list, history)

### 3. Event-Driven Coordination

Saga communicates with workers via Kafka events:
- Saga publishes: `geo.worker.request`, `route.worker.request`, etc.
- Workers publish: `geo.completed`, `geo.failed`, etc.
- Saga reacts to completion/failure and advances state

### 4. Stateless Orchestration

All saga state is persisted via `SagaPersistencePort`:
- Orchestrator service is stateless
- Can be scaled horizontally
- State survives restarts

### 5. Technology Agnostic

All ports are pure Java interfaces:
- No Spring annotations
- No JPA entities
- No Kafka specifics
- Swappable implementations

## Usage by Other Modules

### Domain Implementations

**saga-impl-core**: Business logic implementing command and query ports
- `SagaCommandService` → `SagaCommandPort`
- `SagaQueryService` → `SagaQueryPort`

**saga-impl-jpa**: JPA persistence adapter
- `SagaPersistenceAdapter` → `SagaPersistencePort`

**saga-impl-grpc-server**: gRPC server adapter
- `SagaGrpcService` - Exposes saga operations via gRPC

### Cross-Domain Clients

**saga-grpc-client**: gRPC client stubs for microservice communication
- Used by `app-microservice-itinerary` to query saga status

### Infrastructure Services

**event-saga-orchestrator**: Standalone orchestration service
- Uses `saga-impl-core` for saga management
- Uses `saga-impl-jpa` for persistence
- Publishes events via Kafka

## Saga States

Defined in saga implementation:

```
INITIAL        → Saga created, not yet started
GEOCODING      → Geocoding step in progress
ROUTING        → Routing step in progress
AI_ENRICHMENT  → AI enrichment step in progress
POI_DISCOVERY  → POI discovery step in progress
COMPLETED      → All steps successful ✅
COMPENSATING   → Executing compensation logic
FAILED         → Saga failed after retries ❌
```

## Integration Example

### Monolith Deployment

```java
// event-saga-orchestrator uses saga-impl directly
@Autowired SagaCommandPort commandPort;
@Autowired SagaQueryPort queryPort;

// On worker completion:
commandPort.retrySaga(sagaId);

// REST endpoint for status:
queryPort.getSagasByItinerary(itineraryId);
```

### Microservice Deployment

```java
// app-microservice-itinerary uses gRPC client
@Autowired SagaGrpcClient sagaClient;

// Query saga status remotely:
List<SagaInstanceDto> sagas = sagaClient.getSagasByItinerary(itineraryId);
```

## Error Handling

Saga API provides structured error handling:

**Retry Logic:**
- Automatic retry with exponential backoff (2s → 4s → 8s)
- Max retries: 3 (configurable)
- Manual retry via `SagaCommandPort.retrySaga()`

**Compensation:**
- Triggered after max retries exceeded
- Marks saga and itinerary as FAILED
- Can be manually triggered via `SagaCommandPort.compensateSaga()`

## Monitoring & Observability

Via `SagaQueryPort`:
- Track saga progress by state
- Identify stuck sagas (e.g., in GEOCODING for > 5 minutes)
- Audit saga history via state transitions
- Monitor retry counts and failure reasons

## Future Enhancements

Potential additional ports:
- **SagaTimeoutPort** - Handle saga timeouts
- **SagaMetricsPort** - Expose saga metrics (success rate, avg duration)
- **SagaSchedulingPort** - Schedule saga execution at specific time
- **SagaBatchPort** - Process multiple sagas in batch

## Related Documentation

- [Event Saga Orchestrator](../event-saga-orchestrator/README.md) - Saga orchestration service implementation
- [Implementation Modules](../docs/IMPLEMENTATION_MODULES.md) - All modules overview
- [Saga Pattern](https://microservices.io/patterns/data/saga.html) - Saga pattern explanation
