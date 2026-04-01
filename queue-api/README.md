# Queue API

## Overview

Infrastructure API module defining contracts for queue management and asynchronous processing in the hexagonal architecture. Provides ports for FIFO queuing with priority support, used across the platform for managing itinerary enrichment workflows.

## Purpose

Defines the contracts for:
- FIFO queue operations with priority support
- Queue status monitoring and capacity management
- Processing lifecycle tracking (started, completed, cancelled)
- Cross-domain asynchronous task coordination

## Architecture Role

**Hexagonal Layer**: Ports (Infrastructure Contracts)
- **Outbound Ports**: SPIs for queue management infrastructure

## Module Structure

```
queue-api/
└── port/
    ├── QueuePort.java              # Generic FIFO queue operations
    └── QueueManagementPort.java    # High-level queue management with priority
```

## Ports

### QueuePort<T>

**Generic FIFO queue abstraction** for infrastructure-agnostic queuing:

```java
public interface QueuePort<T> {
    /**
     * Add an item to the tail of the queue
     */
    int enqueue(T item);

    /**
     * Remove and return the item at the head of the queue (non-blocking)
     */
    Optional<T> dequeueNext();

    /**
     * Query the current position of an item in the queue
     */
    int getPosition(UUID itemId);

    /**
     * Remove an item from any position in the queue (cancellation)
     */
    boolean remove(UUID itemId);

    /**
     * Return the current number of items in the queue
     */
    int size();
}
```

**Characteristics:**
- **Generic**: Works with any item type `T`
- **Non-blocking**: `dequeueNext()` returns immediately
- **FIFO ordering**: Items processed in insertion order
- **Random access**: Can query position or remove any item by ID

**Implementations:**
- `queue-impl-redis` - Redis Sorted Set (ZSET) with score-based ordering

### QueueManagementPort

**High-level queue management** with business logic for priority and lifecycle:

```java
public interface QueueManagementPort {
    /**
     * Enqueues an item with priority based on user type
     * (REGISTERED, ADMIN, ANONYMOUS)
     */
    int enqueue(UUID itemId, String userType);

    /**
     * Removes an item from the queue
     */
    void remove(UUID itemId);

    /**
     * Cancels active processing for an item
     */
    void cancelProcessing(UUID itemId);

    /**
     * Returns the current queue status (lengths, capacity)
     */
    QueueStatusDTO.Response getQueueStatus();

    /**
     * Returns the maximum allowed queue size
     */
    int getMaxQueueSize();

    /**
     * Marks an item as processing started (removes from queue)
     */
    boolean markProcessingStarted(UUID itemId);

    /**
     * Gets the current queue position of an item (1-based)
     */
    int getPositionInQueue(UUID itemId);

    /**
     * Marks an item as processing completed (cleanup)
     */
    void markProcessingCompleted(UUID itemId);
}
```

**Priority Support:**
```
Score calculation:
ANONYMOUS   → priority 0 → score = timestamp
REGISTERED  → priority 1 → score = timestamp + 1,000,000,000
ADMIN       → priority 2 → score = timestamp + 2,000,000,000

Within same priority tier: FIFO (lower score = older = first)
```

**Processing Lifecycle:**
```
1. enqueue(itemId, userType) → Item added to queue
2. getPositionInQueue(itemId) → Client polls position
3. markProcessingStarted(itemId) → Saga orchestrator begins processing
4. [Processing occurs...]
5. markProcessingCompleted(itemId) → Cleanup tracking data
```

## Queue Status DTO

```java
// From itinerary-api (cross-domain dependency)
public record QueueStatusDTO.Response(
    int totalQueueLength,         // Total items in queue
    int registeredQueueSize,      // Registered user items
    int anonymousQueueSize,       // Anonymous user items
    List<QueueItem> items         // Sample of queued items
) {}

public record QueueItem(
    UUID itineraryId,
    String title,
    String status,                // QUEUED, PROCESSING
    Integer queuePosition,        // 1-based position
    Instant estimatedCompletion   // ETA
) {}
```

## Dependencies

```xml
<!-- Cross-domain dependency for DTOs -->
<dependency>
    <groupId>com.travel</groupId>
    <artifactId>itinerary-api</artifactId>
</dependency>

<!-- Minimal dependencies -->
<dependency>
    <groupId>jakarta.validation</groupId>
    <artifactId>jakarta.validation-api</artifactId>
</dependency>
```

## Hexagonal Architecture Patterns

### 1. Infrastructure Abstraction

Queue API abstracts queue infrastructure from business logic:

```
Itinerary Service (domain core)
    ↓ depends on
QueueManagementPort (queue-api)
    ↓ implemented by
RedisQueueAdapter (queue-impl-redis)
    ↓ uses
Redis Sorted Set (infrastructure)
```

### 2. Two-Level Abstraction

- **QueuePort** - Low-level, generic queue operations
- **QueueManagementPort** - High-level, business-specific operations with priority

This allows:
- Reuse `QueuePort` for other use cases (email queue, notification queue)
- Domain logic uses `QueueManagementPort` with itinerary-specific concerns

### 3. Cross-Domain Usage

Queue API is used by multiple domains:
- **Itinerary domain** - Queue itinerary enrichment jobs
- **Saga orchestrator** - Coordinate worker execution
- **Future domains** - Any async processing needs

### 4. Technology Agnostic

All ports are pure Java interfaces:
- No Redis specifics
- No Spring annotations
- No messaging framework coupling
- Swappable implementations (Redis → RabbitMQ → Kafka)

### 5. Priority as Business Logic

Priority calculation is in the adapter, not the API:
- API defines `enqueue(itemId, userType)`
- Adapter calculates score based on user type
- Business logic doesn't know about Redis scores

## Usage by Other Modules

### Domain Implementations

**queue-impl-redis**: Redis-based queue implementation
- `RedisQueueAdapter` → `QueuePort<T>`
- `QueueManagementPortAdapter` → `QueueManagementPort`

**Technology**: Spring Data Redis + Lettuce client

### Application Assembly

**app-monolith**:
```xml
<dependency>
    <groupId>com.travel</groupId>
    <artifactId>queue-impl-redis</artifactId>
</dependency>
```

**app-microservice-itinerary**:
```xml
<dependency>
    <groupId>com.travel</groupId>
    <artifactId>queue-impl-redis</artifactId>
</dependency>
```

### Cross-Domain Dependencies

**itinerary-impl-core** uses `QueueManagementPort`:
```java
@Autowired QueueManagementPort queueManagement;

public void createItinerary(Request request) {
    // Persist itinerary
    // ...

    // Enqueue for processing
    int position = queueManagement.enqueue(
        itinerary.getId(),
        getUserType(request)
    );
}
```

**event-saga-orchestrator** uses `QueueManagementPort`:
```java
@Autowired QueueManagementPort queueManagement;

public void startProcessing(UUID itineraryId) {
    // Remove from queue
    queueManagement.markProcessingStarted(itineraryId);

    // Start saga workflow
    // ...
}
```

## Redis Implementation Details

### Data Structures

| Key | Type | Content |
|-----|------|---------|
| `queue:unified` | ZSET | UUID → score (timestamp + priority offset) |
| `queue:metadata:{id}` | HASH | Metadata for each queued item |
| `queue:processing:{id}` | STRING | Items currently being processed |

### Priority Calculation

```java
// In queue-impl-redis adapter:
private double calculateScore(String userType) {
    long timestamp = System.currentTimeMillis();
    int priority = switch (userType) {
        case "ADMIN" -> 2;
        case "REGISTERED" -> 1;
        default -> 0; // ANONYMOUS
    };
    return timestamp + (priority * 1_000_000_000L);
}
```

### Queue Operations

**Enqueue:**
```
ZADD queue:unified <score> <uuid>
HMSET queue:metadata:<uuid> title "..." status "QUEUED" ...
```

**Dequeue:**
```
ZRANGE queue:unified 0 0
ZREM queue:unified <uuid>
DEL queue:metadata:<uuid>
```

**Get Position:**
```
ZRANK queue:unified <uuid> → returns 0-based index
→ convert to 1-based position
```

## Configuration

```yaml
itinerary:
  queue:
    max-concurrent: 3        # Max concurrent processing
    max-queue-size: 100      # Max items in queue
    poll-interval-ms: 1000   # Frequency of queue polling

saga:
  max-concurrent: 5          # Max saga instances active
  poll-interval-ms: 5000     # Saga processing poll interval
```

## Queue Lifecycle

```
┌──────────────────────────────────────────────────────────┐
│                   Queue Lifecycle                        │
└──────────────────────────────────────────────────────────┘

1. Client creates itinerary
   ↓
2. ItineraryService.enqueue(id, userType)
   ↓
3. Item added to Redis ZSET with priority score
   ↓
4. Client polls getPositionInQueue(id) → returns position
   ↓
5. Saga orchestrator polls for available capacity
   ↓
6. Saga orchestrator calls markProcessingStarted(id)
   ↓
7. Item removed from queue (positions auto-update)
   ↓
8. Saga coordinates worker execution
   ↓
9. Saga calls markProcessingCompleted(id) on completion
   ↓
10. Cleanup tracking data
```

## Rate Limiting

Queue provides capacity management:

**Queue Full:**
```java
int queueSize = queueManagement.getQueueStatus().totalQueueLength();
if (queueSize >= queueManagement.getMaxQueueSize()) {
    throw new QueueFullException("Queue at capacity: " + queueSize);
}
```

**Rate Limits:**
- Anonymous users: 5 itineraries/hour
- Registered users: 20 itineraries/hour
- Admins: Unlimited

## Error Handling

**Queue operations are resilient:**
- `remove(itemId)` - Safe if item not in queue (no error)
- `getPosition(itemId)` - Returns -1 if not found
- `markProcessingStarted(itemId)` - Returns false if not in queue

**Cleanup on failure:**
```java
try {
    // Process itinerary
    processItinerary(id);
} catch (Exception e) {
    // Cleanup queue state
    queueManagement.markProcessingCompleted(id);
    throw e;
}
```

## Monitoring

**Queue metrics exposed:**
- Total queue length
- Queue length by priority tier
- Processing capacity utilization
- Average wait time (estimated)
- Items in processing state

**Endpoints:**
```
GET /api/v1/queue/status → QueueStatusDTO
```

## Alternative Implementations

Queue API can be implemented with:
- **Redis** (current) - Fast, in-memory, priority support
- **RabbitMQ** - Durable queues, priority queues, DLQ support
- **Kafka** - Partitioned topics for parallelism
- **PostgreSQL** - SKIP LOCKED for queue semantics
- **AWS SQS** - Managed queue service

## Future Enhancements

Potential additional features:
- **Delayed queue** - Schedule items for future processing
- **Dead letter queue** - Handle repeatedly failed items
- **Queue partitioning** - Separate queues by geography/category
- **Batch dequeue** - Process multiple items at once
- **Queue metrics** - Detailed analytics on queue performance

## Related Documentation

- [Implementation Modules](../docs/IMPLEMENTATION_MODULES.md) - All modules overview
- [Saga API](../saga-api/README.md) - Saga orchestration contracts
- [Event Saga Orchestrator](../event-saga-orchestrator/README.md) - Saga implementation that uses queue
- [Redis as Queue](https://redis.io/docs/data-types/sorted-sets/) - Redis Sorted Set documentation
