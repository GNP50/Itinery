# Event Messaging Starter

## Overview

A Spring Boot auto-configuration starter implementing the **Transactional Outbox Pattern** for reliable event-driven architecture. Guarantees at-least-once delivery of domain events to Apache Kafka while maintaining ACID transaction guarantees.

## Purpose

Solves the **dual-write problem** in distributed systems by:
- **Atomically persisting** events and business state in a single database transaction
- **Asynchronously relaying** events to Kafka with guaranteed delivery
- **Propagating distributed trace IDs** via X-Correlation-Id headers
- **Providing observability** through Micrometer metrics
- **Supporting concurrent processing** with database-level row locking

## Key Components

### EventPublisherPort

Primary outbound port for domain code:

```java
public interface EventPublisherPort {
    void publish(String aggregateType, UUID aggregateId, String eventType, Object payload);
}
```

**Usage in Domain Services:**
```java
@Service
@Transactional
public class ItineraryService {
    private final EventPublisherPort eventPublisher;

    public void createItinerary(CreateItineraryCommand cmd) {
        Itinerary itinerary = repository.save(new Itinerary(...));

        // Published in same transaction
        eventPublisher.publish(
            "itinerary.created",           // Kafka topic
            itinerary.getId(),             // Partition key
            "ItineraryCreatedEvent",       // Event type
            new ItineraryCreatedEvent(...) // Payload
        );
        // Transaction commits atomically
    }
}
```

### OutboxEvent (JPA Entity)

Database table schema:

```sql
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY,
    aggregate_type  VARCHAR(100) NOT NULL,  -- Kafka topic
    aggregate_id    UUID NOT NULL,          -- Partition key
    event_type      VARCHAR(100) NOT NULL,  -- Event type header
    payload         TEXT NOT NULL,          -- JSON-serialized
    correlation_id  VARCHAR(64),            -- Trace ID
    published       BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL,
    published_at    TIMESTAMPTZ,

    INDEX (published, created_at),          -- Polling optimization
    INDEX (aggregate_type, aggregate_id)    -- Event stream queries
);
```

### OutboxPublisher (Scheduled Component)

Background poller that relays events:

- **Polling Frequency**: Every 500ms (configurable)
- **Batch Size**: 100 events per cycle
- **Locking Strategy**: `SELECT … FOR UPDATE SKIP LOCKED`
- **Transaction Boundary**: Each batch is atomic
- **Retry Logic**: Automatic (failed events remain `published=false`)

**Execution Flow:**
```
Poll every 500ms
    ↓
Fetch up to 100 unpublished events (SKIP LOCKED)
    ↓
For each event:
    ├─ Send to Kafka (synchronous, blocking)
    └─ Mark published=true, publishedAt=now
    ↓
Commit transaction
```

### CorrelationIdFilter

Servlet filter for distributed tracing:

- **Reads** `X-Correlation-Id` from incoming HTTP request
- **Generates** new UUID if header missing
- **Stores** in SLF4J MDC for logging
- **Echoes** back to response header
- **Cleans up** MDC on request completion

## Transactional Outbox Pattern

### Problem: Dual-Write

**❌ Naive Approach (Unreliable):**
```java
@Transactional
public void createItinerary(Command cmd) {
    repository.save(itinerary);           // DB write
    kafkaTemplate.send(topic, event);     // Kafka write
}
// What if Kafka fails after DB commits?
// What if app crashes between writes?
```

### Solution: Outbox Pattern

**✅ Reliable Approach:**
```java
@Transactional
public void createItinerary(Command cmd) {
    repository.save(itinerary);           // DB write
    outboxRepository.save(outboxEvent);   // DB write (same transaction)
}
// Single atomic transaction!
// Background poller sends to Kafka asynchronously
```

### Guarantees

1. **At-Least-Once Delivery**: Events may be sent multiple times (consumer must be idempotent)
2. **Causal Ordering**: Events for the same aggregate ID sent in creation order
3. **No Data Loss**: Events survive application crashes between DB commit and Kafka send
4. **Transaction Atomicity**: Business state and events committed together

## Configuration

```yaml
outbox:
  enabled: true                     # Enable/disable starter (default: true)
  publisher:
    fixed-delay-ms: 500             # Poll interval in milliseconds

spring:
  kafka:
    bootstrap-servers: kafka:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      retries: 3
```

## Architecture Pattern

### Hexagonal Architecture Role

- **Layer**: Infrastructure / Outbound Adapter
- **Port**: `EventPublisherPort` (abstraction for event publishing)
- **Adapter**: Transactional Outbox implementation

### Adapter Pattern Example

Domain-specific adapter bridges domain port to starter port:

```java
@Component
public class OutboxEventPublisherAdapter
    implements com.travel.itinerary.api.port.outbound.EventPublisherPort {

    private final com.travel.messaging.EventPublisherPort delegate;

    @Override
    public void publish(Object event) {
        String eventType = event.getClass().getSimpleName();
        String topic = topicMap.get(eventType);
        UUID aggregateId = extractAggregateId(event);

        delegate.publish(topic, aggregateId, eventType, event);
    }
}
```

## Kafka Record Headers

Events sent to Kafka include metadata headers:

| Header | Source | Purpose |
|--------|--------|---------|
| `X-Event-Type` | `eventType` | Consumer routing |
| `X-Aggregate-Type` | `aggregateType` | Domain context |
| `X-Aggregate-Id` | `aggregateId` | Entity identifier |
| `X-Outbox-Event-Id` | `id` | Idempotency token |
| `X-Correlation-Id` | `correlationId` | Distributed tracing |

## Metrics (Micrometer)

| Metric | Type | Description |
|--------|------|-------------|
| `outbox.events.pending` | Gauge | Unpublished events (lag) |
| `outbox.events.published` | Counter | Successfully sent events |
| `outbox.events.failed` | Counter | Failed send attempts |
| `outbox.publish.duration` | Timer | Batch processing latency |

## Error Handling

### Synchronous Path (Event Publication)

- **Failure**: `EventPublicationException` thrown
- **Result**: Transaction rolls back (business + event)
- **Recovery**: Caller handles error

### Asynchronous Path (Event Relay)

- **Failure**: Kafka send error
- **Result**: Batch transaction rolls back, events remain `published=false`
- **Recovery**: Automatic retry on next poll (500ms)

## Usage in Applications

### Add Dependency

```xml
<dependency>
    <groupId>com.travel</groupId>
    <artifactId>event-messaging-starter</artifactId>
</dependency>
```

### Enable JPA Scanning

```java
@SpringBootApplication
@EntityScan(basePackages = "com.travel.messaging")  // Scan OutboxEvent
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### Inject and Use

```java
@Service
public class MyDomainService {
    private final EventPublisherPort eventPublisher;

    @Transactional  // REQUIRED!
    public void performBusinessOperation() {
        // Your business logic
        eventPublisher.publish("my.topic", aggregateId, "MyEvent", payload);
    }
}
```

## Deployment Contexts

| Module | Usage |
|--------|-------|
| `itinerary-impl` | Legacy implementation with outbox integration |
| `itinerary-impl-kafka` | Modular Kafka adapter |
| `app-monolith` | In-process event bus (can also use Kafka) |
| `app-microservice-itinerary` | Kafka-based event distribution |

## Database Requirements

- PostgreSQL 9.5+ (for `SKIP LOCKED` support)
- UUID type support
- TIMESTAMPTZ support

## Scalability

- **Horizontal Scaling**: Multiple instances can poll concurrently (SKIP LOCKED prevents conflicts)
- **Ordering Guarantee**: Per-aggregate ordering preserved via Kafka partition key
- **Batch Processing**: 100 events per poll reduces transaction overhead

## Dependencies

- `spring-boot-starter-data-jpa` - Entity and repository
- `spring-kafka` - Kafka template and producer
- `jackson-databind` - JSON serialization
- `micrometer-core` - Metrics
- `jakarta.servlet-api` (optional) - Correlation filter
