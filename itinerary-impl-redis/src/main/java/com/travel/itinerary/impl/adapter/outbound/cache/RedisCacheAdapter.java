package com.travel.itinerary.impl.adapter.outbound.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.itinerary.api.port.outbound.CachePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Outbound adapter that fulfils {@link CachePort} using Redis as the
 * caching backend.
 *
 * <p>All values are serialised to JSON via Jackson {@link ObjectMapper} before
 * being stored as UTF-8 strings in Redis.  This makes cache entries
 * human-readable when inspected with {@code redis-cli} and avoids coupling to
 * any particular Java serialisation format.
 *
 * <h3>Redis commands used</h3>
 * <ul>
 *   <li>{@code GET}    – retrieve a cached value</li>
 *   <li>{@code SETEX}  – store a value with a TTL (via {@code opsForValue().set})</li>
 *   <li>{@code DEL}    – evict a single key</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisCacheAdapter implements CachePort {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper                  objectMapper;

    // -------------------------------------------------------------------------
    // CachePort
    // -------------------------------------------------------------------------

    /**
     * Retrieve and deserialise a cached value.
     *
     * <p>Returns {@link Optional#empty()} if the key is absent, the value has
     * expired, or deserialisation fails (logged at WARN level so a cache miss
     * is never fatal).
     *
     * @param key  cache key; must not be {@code null}
     * @param <V>  the expected value type; inferred by the caller at the call
     *             site — note that due to Java type erasure the raw
     *             {@link Object} type is returned and the caller is responsible
     *             for casting
     * @return {@link Optional} containing the cached value, or empty
     */
    @Override
    @SuppressWarnings("unchecked")
    public <V> Optional<V> get(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            // Deserialise to a generic Object; the caller is expected to know
            // the concrete type at the point of use.
            V value = (V) objectMapper.readValue(json, Object.class);
            log.trace("Cache hit for key='{}'", key);
            return Optional.ofNullable(value);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to deserialise cached value for key='{}': {}", key, ex.getMessage());
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Unexpected error reading cache key='{}': {}", key, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Retrieve and deserialise a cached value with an explicit target type.
     *
     * <p>This overload is preferred over the raw {@link #get(String)} when the
     * concrete type is known, as it provides type-safe deserialisation.
     *
     * @param key  cache key; must not be {@code null}
     * @param type the {@link Class} to deserialise into
     * @param <V>  the expected value type
     * @return {@link Optional} containing the cached value, or empty
     */
    @Override
    public <V> Optional<V> get(String key, Class<V> type) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            V value = objectMapper.readValue(json, type);
            log.trace("Cache hit for key='{}' type={}", key, type.getSimpleName());
            return Optional.of(value);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to deserialise cached value for key='{}' type={}: {}",
                    key, type.getSimpleName(), ex.getMessage());
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Unexpected error reading cache key='{}': {}", key, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Retrieve and deserialise a cached value using a Jackson {@link JavaType},
     * which supports complex generic types such as {@code List<GeoResult>}.
     *
     * @param key      cache key; must not be {@code null}
     * @param javaType Jackson type descriptor
     * @param <V>      the expected value type
     * @return {@link Optional} containing the cached value, or empty
     */
    public <V> Optional<V> get(String key, JavaType javaType) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            V value = objectMapper.readValue(json, javaType);
            log.trace("Cache hit for key='{}' javaType={}", key, javaType);
            return Optional.of(value);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to deserialise cached value for key='{}': {}", key, ex.getMessage());
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Unexpected error reading cache key='{}': {}", key, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Serialise and store a value with a TTL.
     *
     * <p>Uses Redis {@code SETEX} semantics (via Spring's
     * {@code opsForValue().set(key, value, ttl)}).  Serialisation errors are
     * logged at WARN level and swallowed — a failed cache write must never
     * propagate to the caller.
     *
     * @param key   cache key; must not be {@code null}
     * @param value value to store; must not be {@code null}
     * @param ttl   time-to-live; must be positive and not {@code null}
     * @param <V>   value type
     */
    @Override
    public <V> void put(String key, V value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, ttl);
            log.trace("Cache put key='{}' ttl={}", key, ttl);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialise value for cache key='{}': {}", key, ex.getMessage());
        } catch (Exception ex) {
            log.warn("Unexpected error writing cache key='{}': {}", key, ex.getMessage());
        }
    }

    /**
     * Delete a single cache entry.
     *
     * <p>A {@code false} return from {@code RedisTemplate.delete} (key was not
     * present) is silently ignored — eviction is idempotent.
     *
     * @param key the cache key to remove; must not be {@code null}
     */
    @Override
    public void evict(String key) {
        Boolean deleted = redisTemplate.delete(key);
        if (Boolean.TRUE.equals(deleted)) {
            log.debug("Evicted cache key='{}'", key);
        } else {
            log.trace("Cache evict: key='{}' was not present", key);
        }
    }
}
