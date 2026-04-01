package com.travel.config.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * gRPC client that connects to the {@code config-manager} service and exposes
 * a simple key/value interface for runtime configuration.
 *
 * <h3>Caching</h3>
 * <p>All values retrieved from the remote service are stored in an in-process
 * {@link ConcurrentHashMap}.  Each entry carries an expiry timestamp derived
 * from {@link ConfigClientProperties#getCacheTtlSeconds()}.  A cache miss (or
 * expired entry) triggers a synchronous gRPC call; the result is immediately
 * re-cached before being returned to the caller.
 *
 * <h3>Channel lifecycle</h3>
 * <p>A single {@link ManagedChannel} is created during
 * {@link InitializingBean#afterPropertiesSet()} and shut down gracefully in
 * {@link DisposableBean#destroy()}.
 *
 * <p><strong>Note:</strong> This class references a
 * {@code ConfigManagerGrpc.ConfigManagerBlockingStub} placeholder.  The actual
 * generated stub class will be produced by the protobuf-maven-plugin once the
 * {@code proto/config_manager.proto} descriptor is compiled.  Until then the
 * stub methods are stubbed with descriptive comments so that the rest of the
 * codebase compiles cleanly against the auto-configuration layer.
 */
public class ConfigGrpcClient implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ConfigGrpcClient.class);

    // ── Internal cache entry ─────────────────────────────────────────────────

    private static final class CacheEntry {
        final String value;
        final Instant expiresAt;

        CacheEntry(String value, Instant expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    // ── State ────────────────────────────────────────────────────────────────

    private final ConfigClientProperties properties;
    private final Optional<MeterRegistry> meterRegistry;

    /** Underlying gRPC channel – created once, shared across calls. */
    private volatile ManagedChannel channel;

    /**
     * In-process TTL cache: key → {@link CacheEntry}.
     * Using ConcurrentHashMap for thread-safe read-path; writes are
     * synchronised on the instance monitor to avoid thundering herd.
     */
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * Snapshot of ALL keys fetched via {@link #getAllConfigs()}.
     * Stored separately so individual {@link #getConfig(String)} calls can
     * be served from the cache without a round-trip.
     */
    private volatile Instant allConfigsCachedUntil = Instant.EPOCH;

    // ── Constructor ──────────────────────────────────────────────────────────

    public ConfigGrpcClient(ConfigClientProperties properties,
                            Optional<MeterRegistry> meterRegistry) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    // ── InitializingBean ─────────────────────────────────────────────────────

    @Override
    public void afterPropertiesSet() {
        log.info("Initialising config-client gRPC channel → {}:{}",
                properties.getServerHost(), properties.getServerPort());

        channel = ManagedChannelBuilder
                .forAddress(properties.getServerHost(), properties.getServerPort())
                .usePlaintext()          // TLS should be terminated at the mesh layer
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .maxInboundMessageSize(4 * 1024 * 1024) // 4 MiB
                .build();

        // Eagerly warm the cache so early-startup components get values
        // without blocking the first request.
        try {
            fetchAndCacheAll();
        } catch (Exception ex) {
            log.warn("Eager cache warm-up failed – values will be fetched on demand. Cause: {}",
                    ex.getMessage());
        }
    }

    // ── DisposableBean ───────────────────────────────────────────────────────

    @Override
    public void destroy() throws InterruptedException {
        if (channel != null && !channel.isShutdown()) {
            log.info("Shutting down config-client gRPC channel");
            channel.shutdown();
            if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("gRPC channel did not shut down in 5 s – forcing shutdown");
                channel.shutdownNow();
            }
        }
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Returns the configuration value for {@code key}, or {@link Optional#empty()}
     * if the key is not present in the remote configuration store.
     *
     * <p>The result is served from the in-process cache when possible.  A gRPC
     * call is issued only when the cache entry is absent or expired.
     *
     * @param key the configuration key (must not be {@code null} or blank)
     * @return an {@link Optional} wrapping the string value, never {@code null}
     */
    public Optional<String> getConfig(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Config key must not be null or blank");
        }

        CacheEntry entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            log.trace("Cache hit for key '{}'", key);
            return Optional.of(entry.value);
        }

        return fetchSingleAndCache(key);
    }

    /**
     * Returns a snapshot of <em>all</em> configuration entries visible to this
     * service / namespace combination.
     *
     * <p>The snapshot is fetched in a single gRPC call and the individual entries
     * are stored in the per-key cache.  Subsequent {@link #getConfig(String)} calls
     * will be served from the cache until the TTL expires.
     *
     * @return an unmodifiable view of all key/value pairs; never {@code null}
     */
    public Map<String, String> getAllConfigs() {
        if (Instant.now().isBefore(allConfigsCachedUntil)) {
            log.trace("getAllConfigs(): returning full cache snapshot ({} entries)", cache.size());
            return snapshotCache();
        }
        return fetchAndCacheAll();
    }

    /**
     * Invalidates all locally cached entries, forcing the next
     * {@link #getConfig(String)} or {@link #getAllConfigs()} call to
     * perform a fresh gRPC request.
     */
    public void invalidateCache() {
        log.debug("Invalidating config client cache ({} entries evicted)", cache.size());
        cache.clear();
        allConfigsCachedUntil = Instant.EPOCH;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Fetches a single key from the remote service and stores it in the cache.
     *
     * <p><strong>Stub replacement point:</strong> replace the body of this method
     * with a real {@code ConfigManagerGrpc.ConfigManagerBlockingStub#getConfig()}
     * call once the proto-generated sources are available.
     */
    private synchronized Optional<String> fetchSingleAndCache(String key) {
        // Double-checked locking: another thread may have populated while we waited.
        CacheEntry existing = cache.get(key);
        if (existing != null && !existing.isExpired()) {
            return Optional.of(existing.value);
        }

        Timer.Sample sample = startTimer();
        try {
            /*
             * TODO: replace with real gRPC call, e.g.:
             *
             *   GetConfigRequest request = GetConfigRequest.newBuilder()
             *       .setServiceName(properties.getServiceName())
             *       .setNamespace(properties.getDefaultNamespace())
             *       .setKey(key)
             *       .build();
             *   GetConfigResponse response = stub.getConfig(request);
             *   String value = response.getValue();
             */
            log.debug("Fetching config key '{}' from {}:{}", key,
                    properties.getServerHost(), properties.getServerPort());

            // Placeholder: in production this value comes from the gRPC response.
            String value = fetchValueOverGrpc(key);

            if (value == null) {
                return Optional.empty();
            }

            Instant expiresAt = Instant.now().plusSeconds(properties.getCacheTtlSeconds());
            cache.put(key, new CacheEntry(value, expiresAt));
            stopTimer(sample, "success");
            return Optional.of(value);

        } catch (StatusRuntimeException ex) {
            stopTimer(sample, "error");
            log.error("gRPC error fetching config key '{}': {} – {}",
                    key, ex.getStatus().getCode(), ex.getMessage());
            // Return stale value if available, otherwise empty.
            CacheEntry stale = cache.get(key);
            return stale != null ? Optional.of(stale.value) : Optional.empty();
        }
    }

    /**
     * Fetches all configs for the configured service + namespace and populates
     * the cache.
     */
    private synchronized Map<String, String> fetchAndCacheAll() {
        // Double-checked locking.
        if (Instant.now().isBefore(allConfigsCachedUntil)) {
            return snapshotCache();
        }

        Timer.Sample sample = startTimer();
        try {
            log.debug("Fetching all configs for service='{}' namespace='{}' from {}:{}",
                    properties.getServiceName(), properties.getDefaultNamespace(),
                    properties.getServerHost(), properties.getServerPort());

            /*
             * TODO: replace with real gRPC call, e.g.:
             *
             *   GetAllConfigsRequest request = GetAllConfigsRequest.newBuilder()
             *       .setServiceName(properties.getServiceName())
             *       .setNamespace(properties.getDefaultNamespace())
             *       .build();
             *   GetAllConfigsResponse response = stub.getAllConfigs(request);
             *   Map<String, String> remoteValues = response.getEntriesMap();
             */
            Map<String, String> remoteValues = fetchAllValuesOverGrpc();

            Instant expiresAt = Instant.now().plusSeconds(properties.getCacheTtlSeconds());
            remoteValues.forEach((k, v) -> cache.put(k, new CacheEntry(v, expiresAt)));
            allConfigsCachedUntil = expiresAt;

            stopTimer(sample, "success");
            log.info("Config cache populated with {} entries (TTL={}s)",
                    remoteValues.size(), properties.getCacheTtlSeconds());
            return Collections.unmodifiableMap(new HashMap<>(remoteValues));

        } catch (StatusRuntimeException ex) {
            stopTimer(sample, "error");
            log.error("gRPC error fetching all configs: {} – {}",
                    ex.getStatus().getCode(), ex.getMessage());
            return snapshotCache();
        }
    }

    /**
     * Low-level stub invocation for a single key.
     * Replace with the generated blocking-stub call.
     */
    private String fetchValueOverGrpc(String key) {
        // Real implementation delegates to the generated blocking stub.
        // This method exists solely so that the auto-configuration compiles
        // without the proto-generated sources.
        throw new UnsupportedOperationException(
                "Replace fetchValueOverGrpc() with the generated gRPC stub call. "
                        + "Channel is available via the 'channel' field.");
    }

    /**
     * Low-level stub invocation for all keys.
     * Replace with the generated blocking-stub call.
     */
    private Map<String, String> fetchAllValuesOverGrpc() {
        throw new UnsupportedOperationException(
                "Replace fetchAllValuesOverGrpc() with the generated gRPC stub call. "
                        + "Channel is available via the 'channel' field.");
    }

    /** Returns an unmodifiable snapshot of the current non-expired cache entries. */
    private Map<String, String> snapshotCache() {
        Map<String, String> snapshot = new HashMap<>(cache.size());
        cache.forEach((k, v) -> {
            if (!v.isExpired()) {
                snapshot.put(k, v.value);
            }
        });
        return Collections.unmodifiableMap(snapshot);
    }

    // ── Metrics helpers ──────────────────────────────────────────────────────

    private Timer.Sample startTimer() {
        return meterRegistry
                .map(Timer::start)
                .orElse(null);
    }

    private void stopTimer(Timer.Sample sample, String outcome) {
        if (sample == null) return;
        meterRegistry.ifPresent(registry ->
                sample.stop(Timer.builder("config.client.grpc.request")
                        .description("Latency of config-manager gRPC calls")
                        .tag("outcome", outcome)
                        .tag("service", properties.getServiceName())
                        .register(registry)));
    }

    // ── Accessors (for testing / actuator exposure) ──────────────────────────

    /** Returns the underlying {@link ManagedChannel} for diagnostic purposes. */
    public ManagedChannel getChannel() {
        return channel;
    }

    /** Returns the number of entries currently held in the cache. */
    public int getCacheSize() {
        return cache.size();
    }
}
