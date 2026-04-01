package com.travel.itinerary.api.port.outbound;

import java.time.Duration;
import java.util.Optional;

/**
 * Outbound SPI: generic key/value cache with optional TTL support.
 * <p>
 * Implementations may use Caffeine (in-process), Redis, Memcached, or any
 * compatible caching technology.
 */
public interface CachePort {

    /**
     * Retrieve a cached value by key (untyped).
     * Prefer {@link #get(String, Class)} when the concrete type is known, to
     * avoid {@code ClassCastException} on JSON-serialised backends.
     *
     * @param key the cache key; must not be {@code null}
     * @param <V> the expected value type
     * @return an {@link Optional} containing the cached value, or empty if the
     *         key is absent or the entry has expired
     */
    <V> Optional<V> get(String key);

    /**
     * Retrieve and deserialise a cached value with an explicit target type.
     * <p>
     * Implementations that serialise values to JSON (e.g. Redis) must use this
     * overload to deserialise correctly; calling the untyped {@link #get(String)}
     * will return a {@code LinkedHashMap} instead of the concrete type.
     *
     * @param key  the cache key; must not be {@code null}
     * @param type the target class to deserialise into; must not be {@code null}
     * @param <V>  the expected value type
     * @return an {@link Optional} containing the typed cached value, or empty
     */
    <V> Optional<V> get(String key, Class<V> type);

    /**
     * Store a value in the cache with a time-to-live.
     *
     * @param key   the cache key; must not be {@code null}
     * @param value the value to store; must not be {@code null}
     * @param ttl   duration after which the entry is automatically evicted;
     *              must be positive and not {@code null}
     * @param <V>   the value type
     */
    <V> void put(String key, V value, Duration ttl);

    /**
     * Remove a single entry from the cache.
     *
     * @param key the cache key to evict; must not be {@code null}
     */
    void evict(String key);
}
