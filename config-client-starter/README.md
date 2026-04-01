# Config Client Starter

## Overview

A Spring Boot auto-configuration starter that provides centralized configuration management via gRPC. Implements an intelligent caching layer to minimize remote calls while maintaining fresh configuration data.

## Purpose

Enables applications to fetch configuration key-value pairs from a remote `config-manager` service, supporting:
- **Centralized configuration management** across distributed services
- **Namespace isolation** (e.g., production, staging, development)
- **In-memory TTL-based caching** to reduce network latency
- **Graceful lifecycle management** with connection pooling
- **Observability** through Micrometer metrics

## Key Components

### ConfigGrpcClient

The main client bean exposed by this starter.

**Public API:**
```java
@Autowired
private ConfigGrpcClient configClient;

// Fetch single configuration
Optional<String> dbUrl = configClient.getConfig("database.url");

// Fetch all configurations
Map<String, String> allConfigs = configClient.getAllConfigs();

// Manually invalidate cache
configClient.invalidateCache();
```

**Features:**
- Thread-safe `ConcurrentHashMap` caching with per-entry TTL
- Automatic cache warm-up during application startup
- Synchronous gRPC calls with circuit breaker support
- Optional Micrometer timer metrics (`config.client.grpc.request`)

### ConfigClientProperties

Configuration bound to `config.client.*` prefix:

```yaml
config:
  client:
    enabled: true                    # Enable/disable starter (default: true)
    server-host: localhost           # gRPC server hostname
    server-port: 9090                # gRPC server port
    service-name: my-service         # Logical service identifier
    default-namespace: production    # Configuration environment/namespace
    cache-ttl-seconds: 60            # Cache entry TTL in seconds
```

## Architecture Pattern

### Hexagonal Architecture Role

- **Layer**: Infrastructure / Outbound Adapter
- **Port**: Configuration retrieval abstraction
- **Adapter**: gRPC-based implementation

### Dependency Flow

```
Application Code
    ↓ (depends on)
ConfigGrpcClient (bean provided by starter)
    ↓ (communicates via)
gRPC Channel → config-manager service
```

## Usage in Applications

### Monolith Deployment

```xml
<dependency>
    <groupId>com.travel</groupId>
    <artifactId>config-client-starter</artifactId>
</dependency>
```

```yaml
config:
  client:
    enabled: false  # Disabled - uses local application.yml
```

### Microservice Deployment

```xml
<dependency>
    <groupId>com.travel</groupId>
    <artifactId>config-client-starter</artifactId>
</dependency>
```

```yaml
config:
  client:
    enabled: true
    server-host: config-manager
    server-port: 9090
    service-name: itinerary-microservice
    default-namespace: production
    cache-ttl-seconds: 60
```

## Lifecycle Management

**Startup:**
1. Auto-configuration creates `ConfigGrpcClient` bean
2. gRPC channel initialized with keep-alive settings
3. Cache warm-up attempted (non-fatal if fails)
4. Bean ready for injection

**Runtime:**
- Cache-hit: O(1) lookup, immediate return
- Cache-miss: Synchronized fetch, gRPC call, cache update
- Timer metrics recorded for each remote call

**Shutdown:**
- Graceful channel shutdown (5-second timeout)
- Forced shutdown if not terminated
- Pending RPCs cancelled

## Dependencies

- `spring-boot-autoconfigure` - Auto-configuration infrastructure
- `grpc-stub`, `grpc-netty-shaded`, `grpc-protobuf` - gRPC client stack
- `micrometer-core` (optional) - Metrics recording

## Metrics

| Metric | Type | Tags | Description |
|--------|------|------|-------------|
| `config.client.grpc.request` | Timer | `outcome`, `service` | gRPC call latency |

## Thread Safety

- Read operations: Lock-free via `ConcurrentHashMap`
- Write operations: Synchronized with double-checked locking
- No blocking on cache hits

## Error Handling

- gRPC failures logged with detailed status codes
- Returns stale cache value if available during errors
- Cache expiry forces retry on next access
