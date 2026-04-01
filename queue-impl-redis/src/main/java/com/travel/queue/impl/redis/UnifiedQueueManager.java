package com.travel.queue.impl.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.queue.port.QueueManagementPort;
import com.travel.queue.port.QueuePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * Unified Queue Manager using Redis Sorted Set for FIFO-with-priority queue.
 *
 * <p>This manager replaces the separate registered/anonymous queues with a single
 * sorted set that guarantees FIFO ordering within each priority tier.</p>
 *
 * <h3>Key Features:</h3>
 * <ul>
 *   <li>Single source of truth for queue state (Redis ZSET)</li>
 *   <li>FIFO within priority tiers (ADMIN > REGISTERED > ANONYMOUS)</li>
 *   <li>O(log N) position queries via ZRANK</li>
 *   <li>Accurate positions that never become stale</li>
 *   <li>Automatic dequeue when processing starts</li>
 * </ul>
 *
 * <h3>Redis Data Structures:</h3>
 * <pre>
 * queue:unified (ZSET)
 *   - members: itineraryId (UUID string)
 *   - score: timestamp_ms + (priority_level × 1,000,000,000)
 *   - operations: ZADD, ZRANK, ZREM, ZRANGE, ZCARD
 *
 * queue:metadata (HASH)
 *   - field: itineraryId
 *   - value: {"userType": "REGISTERED", "userId": "...", "enqueuedAt": "..."}
 *
 * queue:processing (HASH)
 *   - field: itineraryId
 *   - value: timestamp when processing started
 * </pre>
 *
 * <h3>Priority Levels:</h3>
 * <ul>
 *   <li>ADMIN = 0 → score offset +0 (highest priority)</li>
 *   <li>REGISTERED = 1 → score offset +1,000,000,000</li>
 *   <li>ANONYMOUS = 2 → score offset +2,000,000,000 (lowest priority)</li>
 * </ul>
 *
 * <h3>Score Calculation:</h3>
 * <pre>
 * score = System.currentTimeMillis() + priorityOffset
 * Lower score = higher priority + earlier in FIFO queue
 *
 * Example:
 *   ADMIN item at timestamp 1711234567890:
 *     score = 1711234567890 + 0 = 1711234567890
 *
 *   ADMIN item at timestamp 1711234569000:
 *     score = 1711234569000 + 0 = 1711234569000
 *
 *   REGISTERED item at timestamp 1711234570000:
 *     score = 1711234570000 + 1e9 = 2711234570000
 *
 *   ANONYMOUS item at timestamp 1711234571000:
 *     score = 1711234571000 + 2e9 = 3711234571000
 *
 * ZSET ascending order (ZRANGE):
 *   [ADMIN_oldest (1711234567890), ADMIN_2nd (1711234569000), REGISTERED_oldest (2711...), ANONYMOUS_oldest (3711...)]
 *
 * Extraction uses ZRANGE (ascending) → strict FIFO within priority tiers
 * </pre>
 *
 * @since 1.0
 */
@Slf4j
@Component
public class UnifiedQueueManager {

    // -------------------------------------------------------------------------
    // Redis Keys Constants
    // -------------------------------------------------------------------------

    /** Main unified queue (sorted set). */
    public static final String QUEUE_UNIFIED = "queue:unified";

    /** Metadata hash map for queued items. */
    public static final String QUEUE_METADATA = "queue:metadata";

    /** Currently processing items tracker. */
    public static final String QUEUE_PROCESSING = "queue:processing";

    // -------------------------------------------------------------------------
    // Priority Level Constants
    // -------------------------------------------------------------------------

    /**
     * Priority offset for ADMIN users (highest priority).
     * Lower score = higher priority, so ADMIN gets offset 0.
     */
    private static final long PRIORITY_ADMIN = 0L;

    /**
     * Priority offset for REGISTERED users (medium priority).
     */
    private static final long PRIORITY_REGISTERED = 1_000_000_000L;

    /**
     * Priority offset for ANONYMOUS users (lowest priority).
     * Highest offset = lowest priority.
     */
    private static final long PRIORITY_ANONYMOUS = 2_000_000_000L;

    // -------------------------------------------------------------------------
    // Dependencies
    // -------------------------------------------------------------------------

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    // -------------------------------------------------------------------------
    // Lua Scripts for Atomic Operations
    // -------------------------------------------------------------------------

    /**
     * Atomic enqueue script: ZADD + HSET in one operation.
     * Prevents orphaned data if crash occurs between operations.
     *
     * KEYS[1] = queue:unified (ZSET)
     * KEYS[2] = queue:metadata (HASH)
     * ARGV[1] = itineraryId (UUID string)
     * ARGV[2] = score (double)
     * ARGV[3] = metadata JSON (string)
     *
     * Returns: 1 if added, 0 if already exists
     */
    private final RedisScript<Long> enqueueScript;

    /**
     * Atomic fetch and mark script: ZRANGE + ZREM in one operation.
     * Prevents race condition where multiple workers fetch the same items.
     *
     * KEYS[1] = queue:unified (ZSET)
     * KEYS[2] = queue:metadata (HASH)
     * KEYS[3] = queue:processing (HASH)
     * ARGV[1] = count (number of items to fetch)
     * ARGV[2] = current timestamp (for processing tracker)
     *
     * Returns: JSON array of itineraryIds that were fetched and removed
     */
    private final RedisScript<List> fetchAndMarkScript;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public UnifiedQueueManager(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;

        // Initialize enqueue script
        this.enqueueScript = new DefaultRedisScript<>();
        ((DefaultRedisScript<Long>) this.enqueueScript).setScriptText(
                """
                local added = redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1])
                redis.call('HSET', KEYS[2], ARGV[1], ARGV[3])
                return added
                """
        );
        ((DefaultRedisScript<Long>) this.enqueueScript).setResultType(Long.class);

        // Initialize fetch and mark script
        this.fetchAndMarkScript = new DefaultRedisScript<>();
        ((DefaultRedisScript<List>) this.fetchAndMarkScript).setScriptText(
                """
                local items = redis.call('ZRANGE', KEYS[1], 0, tonumber(ARGV[1]) - 1)
                if #items == 0 then
                    return {}
                end
                for i, item in ipairs(items) do
                    redis.call('ZREM', KEYS[1], item)
                    redis.call('HDEL', KEYS[2], item)
                    redis.call('HSET', KEYS[3], item, ARGV[2])
                end
                return items
                """
        );
        ((DefaultRedisScript<List>) this.fetchAndMarkScript).setResultType(List.class);
    }

    // -------------------------------------------------------------------------
    // Enqueue Operations
    // -------------------------------------------------------------------------

    /**
     * Enqueues an itinerary with the specified user type priority.
     *
     * <p>The item is added to the unified sorted set with a score calculated from
     * the current timestamp and the user type's priority level. This ensures FIFO
     * ordering within each priority tier.</p>
     *
     * <p><strong>Atomicity:</strong> Uses Lua script to perform ZADD + HSET atomically,
     * preventing orphaned data in case of crashes.</p>
     *
     * @param itineraryId the itinerary UUID to enqueue; must not be {@code null}
     * @param userType the user type ("ADMIN", "REGISTERED", or "ANONYMOUS")
     * @param userId optional user ID for tracking (may be null for anonymous)
     * @return 1-based position in the queue immediately after enqueuing
     * @throws IllegalArgumentException if userType is invalid
     */
    public int enqueue(UUID itineraryId, String userType, UUID userId) {
        Objects.requireNonNull(itineraryId, "itineraryId must not be null");
        Objects.requireNonNull(userType, "userType must not be null");

        // Calculate score based on priority and timestamp
        long priorityOffset = getPriorityOffset(userType);
        long timestamp = System.currentTimeMillis();
        double score = timestamp + priorityOffset;

        // Prepare metadata
        QueueMetadata metadata = new QueueMetadata(
                userType,
                userId != null ? userId.toString() : null,
                Instant.now().toString(),
                getPriorityLevel(userType)
        );

        String metadataJson;
        try {
            metadataJson = objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize metadata for itinerary={}", itineraryId, e);
            throw new IllegalStateException("Metadata serialization failed", e);
        }

        // Execute atomic enqueue (ZADD + HSET in Lua script)
        Long added = redisTemplate.execute(
                enqueueScript,
                List.of(QUEUE_UNIFIED, QUEUE_METADATA),
                itineraryId.toString(),
                String.valueOf(score),
                metadataJson
        );

        if (Long.valueOf(0L).equals(added)) {
            log.warn("Item already exists in queue: itineraryId={}", itineraryId);
        }

        // Get position
        int position = getPosition(itineraryId);

        log.info("Enqueued itinerary id={} userType={} score={} position={}",
                itineraryId, userType, score, position);

        return position;
    }

    // -------------------------------------------------------------------------
    // Dequeue Operations
    // -------------------------------------------------------------------------

    /**
     * Marks an itinerary as processing started and removes it from the queue.
     *
     * <p>This is the critical operation that ensures queue positions remain accurate.
     * When a saga starts processing, the item is removed from the queue (ZREM),
     * causing all other items' positions to automatically update.</p>
     *
     * @param itineraryId the itinerary UUID to mark as processing
     * @return {@code true} if the item was in the queue and removed, {@code false} otherwise
     */
    public boolean markProcessingStarted(UUID itineraryId) {
        Objects.requireNonNull(itineraryId, "itineraryId must not be null");

        String key = itineraryId.toString();

        // Remove from queue (DEQUEUE!)
        Long removed = redisTemplate.opsForZSet().remove(QUEUE_UNIFIED, key);

        if (removed == null || removed == 0) {
            log.warn("Item not found in queue when marking processing: itineraryId={}", itineraryId);
            return false;
        }

        // Remove metadata
        redisTemplate.opsForHash().delete(QUEUE_METADATA, key);

        // Track as processing
        redisTemplate.opsForHash().put(
                QUEUE_PROCESSING,
                key,
                Instant.now().toString()
        );

        log.info("Marked processing started: itineraryId={} (removed from queue)", itineraryId);

        return true;
    }

    /**
     * Marks an itinerary as processing completed and cleans up tracking.
     *
     * @param itineraryId the itinerary UUID
     */
    public void markProcessingCompleted(UUID itineraryId) {
        Objects.requireNonNull(itineraryId, "itineraryId must not be null");

        redisTemplate.opsForHash().delete(QUEUE_PROCESSING, itineraryId.toString());

        log.debug("Marked processing completed: itineraryId={}", itineraryId);
    }

    // -------------------------------------------------------------------------
    // Position Query Operations
    // -------------------------------------------------------------------------

    /**
     * Gets the current queue position of an itinerary.
     *
     * <p>Uses Redis ZRANK to get the 0-based rank, then converts to 1-based position.
     * This operation is O(log N) and always returns the current accurate position.</p>
     *
     * @param itineraryId the itinerary UUID to locate
     * @return 1-based position in the queue, or {@code -1} if not found
     */
    public int getPosition(UUID itineraryId) {
        Objects.requireNonNull(itineraryId, "itineraryId must not be null");

        Long rank = redisTemplate.opsForZSet().rank(QUEUE_UNIFIED, itineraryId.toString());

        if (rank == null) {
            log.trace("Item not found in queue: itineraryId={}", itineraryId);
            return -1;
        }

        // Convert 0-based rank to 1-based position
        int position = rank.intValue() + 1;

        log.trace("Position query: itineraryId={} position={}", itineraryId, position);

        return position;
    }

    /**
     * Removes an itinerary from the queue (e.g., user cancellation).
     *
     * @param itineraryId the itinerary UUID to remove
     * @return {@code true} if the item was found and removed, {@code false} otherwise
     */
    public boolean remove(UUID itineraryId) {
        Objects.requireNonNull(itineraryId, "itineraryId must not be null");

        String key = itineraryId.toString();

        Long removed = redisTemplate.opsForZSet().remove(QUEUE_UNIFIED, key);
        redisTemplate.opsForHash().delete(QUEUE_METADATA, key);

        boolean wasRemoved = removed != null && removed > 0;

        if (wasRemoved) {
            log.info("Removed itinerary from queue: itineraryId={}", itineraryId);
        } else {
            log.debug("Item not in queue (already removed?): itineraryId={}", itineraryId);
        }

        return wasRemoved;
    }

    // -------------------------------------------------------------------------
    // Batch Operations
    // -------------------------------------------------------------------------

    /**
     * Fetches the top N items from the queue for processing.
     *
     * <p>Used by the saga scheduler to get the next batch of items to process
     * when processing slots become available.</p>
     *
     * <p><strong>Atomicity:</strong> Uses Lua script to perform ZRANGE + ZREM + metadata cleanup
     * atomically. This prevents race conditions where multiple workers fetch the same batch.
     * The fetched items are automatically removed from the queue and marked as processing.</p>
     *
     * <p><strong>Important:</strong> Calling {@code markProcessingStarted()} after this method
     * is optional - items are already removed and marked. Subsequent calls to
     * {@code markProcessingStarted()} for these items will return {@code false}.</p>
     *
     * @param count the maximum number of items to fetch
     * @return list of itinerary UUIDs in priority order (already removed from queue)
     */
    public List<UUID> fetchTopItems(int count) {
        if (count <= 0) {
            return Collections.emptyList();
        }

        // Execute atomic fetch+mark (ZRANGE + ZREM + metadata cleanup in Lua script)
        List<String> items = redisTemplate.execute(
                fetchAndMarkScript,
                List.of(QUEUE_UNIFIED, QUEUE_METADATA, QUEUE_PROCESSING),
                String.valueOf(count),
                Instant.now().toString()
        );

        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> result = new ArrayList<>(items.size());
        for (String item : items) {
            try {
                result.add(UUID.fromString(item));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid UUID in queue, skipping: {}", item);
            }
        }

        log.info("Fetched and reserved {} items from queue atomically", result.size());

        return result;
    }

    /**
     * Returns the total number of items in the queue.
     *
     * @return queue size; always {@code >= 0}
     */
    public long size() {
        Long size = redisTemplate.opsForZSet().zCard(QUEUE_UNIFIED);
        return (size != null) ? size : 0L;
    }

    /**
     * Returns all itinerary IDs currently in the queue, ordered by priority and FIFO.
     *
     * <p>Items are returned in processing order: highest priority and oldest first.</p>
     *
     * @return list of all itinerary UUIDs in the queue
     */
    public List<UUID> getAllQueuedItems() {
        // Get all items using ZRANGE (ascending order = correct priority + FIFO)
        Set<String> items = redisTemplate.opsForZSet().range(QUEUE_UNIFIED, 0, -1);

        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> result = new ArrayList<>(items.size());
        for (String item : items) {
            try {
                result.add(UUID.fromString(item));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid UUID in queue, skipping: {}", item);
            }
        }

        return result;
    }

    /**
     * Returns the number of items currently being processed.
     *
     * @return processing count
     */
    public long processingCount() {
        Long size = redisTemplate.opsForHash().size(QUEUE_PROCESSING);
        return (size != null) ? size : 0L;
    }

    // -------------------------------------------------------------------------
    // Statistics Operations
    // -------------------------------------------------------------------------

    /**
     * Gets statistics broken down by user type.
     *
     * @return queue statistics
     */
    public QueueStatistics getStatistics() {
        long totalQueued = size();
        long totalProcessing = processingCount();

        // Count by user type (requires scanning metadata)
        Map<String, Long> countsByUserType = new HashMap<>();
        countsByUserType.put("ADMIN", 0L);
        countsByUserType.put("REGISTERED", 0L);
        countsByUserType.put("ANONYMOUS", 0L);

        // Note: This is O(N) operation - use sparingly or cache
        Map<Object, Object> allMetadata = redisTemplate.opsForHash().entries(QUEUE_METADATA);
        for (Object value : allMetadata.values()) {
            try {
                QueueMetadata metadata = objectMapper.readValue((String) value, QueueMetadata.class);
                countsByUserType.merge(metadata.userType, 1L, Long::sum);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse metadata", e);
            }
        }

        return new QueueStatistics(
                totalQueued,
                totalProcessing,
                countsByUserType.get("REGISTERED"),
                countsByUserType.get("ANONYMOUS"),
                countsByUserType.get("ADMIN")
        );
    }

    // -------------------------------------------------------------------------
    // Priority Mapping
    // -------------------------------------------------------------------------

    /**
     * Maps user type to priority offset.
     *
     * @param userType the user type string
     * @return priority offset value
     */
    private long getPriorityOffset(String userType) {
        return switch (userType.toUpperCase()) {
            case "ADMIN" -> PRIORITY_ADMIN;
            case "REGISTERED" -> PRIORITY_REGISTERED;
            case "ANONYMOUS" -> PRIORITY_ANONYMOUS;
            default -> throw new IllegalArgumentException("Invalid user type: " + userType);
        };
    }

    /**
     * Maps user type to priority level integer.
     *
     * @param userType the user type string
     * @return priority level (0-2)
     */
    private int getPriorityLevel(String userType) {
        return switch (userType.toUpperCase()) {
            case "ADMIN" -> 2;
            case "REGISTERED" -> 1;
            case "ANONYMOUS" -> 0;
            default -> throw new IllegalArgumentException("Invalid user type: " + userType);
        };
    }

    // -------------------------------------------------------------------------
    // Internal Data Classes
    // -------------------------------------------------------------------------

    /**
     * Metadata stored for each queued item.
     */
    record QueueMetadata(
            String userType,
            String userId,
            String enqueuedAt,
            int priority
    ) {}

    /**
     * Queue statistics breakdown.
     */
    public record QueueStatistics(
            long totalQueued,
            long totalProcessing,
            long registeredCount,
            long anonymousCount,
            long adminCount
    ) {}
}
