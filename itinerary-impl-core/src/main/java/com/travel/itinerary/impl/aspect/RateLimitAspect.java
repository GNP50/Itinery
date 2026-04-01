package com.travel.itinerary.impl.aspect;

import com.travel.itinerary.impl.annotation.RateLimit;
import com.travel.itinerary.impl.exception.RateLimitExceededException;
import com.travel.user.api.dto.AuthDTO;
import com.travel.user.api.port.inbound.AuthUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * AOP aspect that intercepts methods annotated with {@link RateLimit} to enforce
 * per-user rate limiting using Redis.
 *
 * <h3>Algorithm: Fixed-Window Counter</h3>
 * <pre>
 * 1. Extract user identifier (userId for registered, sessionId for anonymous)
 * 2. Get user type (ADMIN, REGISTERED, ANONYMOUS)
 * 3. Calculate window bucket: windowStart = now - (now % windowSeconds)
 * 4. Build Redis key: rate_limit:user:{userId}:{windowStart}
 * 5. Atomically increment counter (INCR)
 * 6. Set TTL to windowSeconds * 2 (cleanup old windows)
 * 7. Determine limit based on user type:
 *    - ADMIN: 100 requests/hour
 *    - REGISTERED: 20 requests/hour
 *    - ANONYMOUS: 5 requests/hour
 * 8. If count > limit, throw RateLimitExceededException
 * 9. Otherwise, proceed with method execution
 * </pre>
 *
 * <h3>Redis Key Structure:</h3>
 * <pre>
 * rate_limit:user:{userId}:{windowStart} → STRING (counter)
 * TTL: windowSeconds * 2
 * </pre>
 *
 * <h3>Example:</h3>
 * <pre>
 * Anonymous user (userId="anon_abc123") creating itinerary at 2026-03-29 10:30:45
 * Window: 3600 seconds (1 hour)
 * WindowStart: 1711710000 (truncated to hour boundary)
 * Key: rate_limit:user:anon_abc123:1711710000
 * Limit: 5 requests
 * Current count after INCR: 6
 * Result: RateLimitExceededException (retryAfter = 1755 seconds)
 * </pre>
 *
 * @since 1.0
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RedisTemplate<String, String> redisTemplate;
    private final AuthUseCase authUseCase;

    /**
     * Intercepts methods annotated with {@link RateLimit} and enforces rate limiting.
     *
     * @param joinPoint the method invocation join point
     * @param rateLimit the rate limit annotation with configuration
     * @return the result of the method execution
     * @throws Throwable if the method execution throws an exception
     * @throws RateLimitExceededException if rate limit is exceeded
     */
    @Around("@annotation(rateLimit)")
    public Object checkRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        // 1. Get user identifier and type
        AuthDTO.StatusResponse authStatus = authUseCase.getStatus();
        String userId = authStatus.userId().toString();
        String userType = authStatus.userType();

        if (userId == null) {
            log.warn("No user context available, skipping rate limit check");
            return joinPoint.proceed();
        }

        // 2. Calculate window bucket
        long now = System.currentTimeMillis() / 1000; // Convert to seconds
        long windowStart = now - (now % rateLimit.windowSeconds());

        // 3. Build Redis key
        String key = buildRateLimitKey(rateLimit, userId, windowStart);

        // 4. Atomically increment counter
        Long count = redisTemplate.opsForValue().increment(key);

        if (count == null) {
            log.error("Redis INCR returned null for key={}", key);
            // Fail open: allow request if Redis is having issues
            return joinPoint.proceed();
        }

        // 5. Set TTL if this is the first request in this window
        if (count == 1) {
            redisTemplate.expire(key, rateLimit.windowSeconds() * 2, TimeUnit.SECONDS);
        }

        // 6. Determine limit based on user type
        int limit = getLimitForUserType(userType);

        // 7. Check if limit is exceeded
        if (count > limit) {
            long retryAfter = rateLimit.windowSeconds() - (now - windowStart);

            log.warn("Rate limit exceeded: userId={} userType={} count={}/{} key={} retryAfter={}s",
                    userId, userType, count, limit, key, retryAfter);

            throw new RateLimitExceededException(
                    String.format("Rate limit exceeded: %d/%d requests per %d seconds",
                            count, limit, rateLimit.windowSeconds()),
                    retryAfter,
                    limit,
                    count.intValue(),
                    userType
            );
        }

        // 8. Log successful check
        log.debug("Rate limit check passed: userId={} userType={} count={}/{} key={}",
                userId, userType, count, limit, key);

        // 9. Proceed with method execution
        return joinPoint.proceed();
    }

    /**
     * Builds the Redis key for rate limiting.
     *
     * @param rateLimit the rate limit annotation
     * @param userId the user identifier
     * @param windowStart the window start timestamp
     * @return the Redis key
     */
    private String buildRateLimitKey(RateLimit rateLimit, String userId, long windowStart) {
        String keyPrefix = rateLimit.key();

        // If no custom key provided, use method name
        if (keyPrefix == null || keyPrefix.isEmpty()) {
            keyPrefix = "default";
        }

        return String.format("rate_limit:user:%s:%s:%d", keyPrefix, userId, windowStart);
    }

    /**
     * Gets the method name from the join point for key generation.
     *
     * @param joinPoint the method invocation join point
     * @return the method name in format "ClassName.methodName"
     */
    private String getMethodName(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }

    /**
     * Determines the rate limit based on user type.
     *
     * <p>Conservative limits that protect against abuse while maintaining
     * good UX for registered users:</p>
     * <ul>
     *   <li>ADMIN: 100 requests/hour</li>
     *   <li>REGISTERED: 20 requests/hour</li>
     *   <li>ANONYMOUS: 5 requests/hour</li>
     * </ul>
     *
     * @param userType the user type string
     * @return the request limit
     */
    private int getLimitForUserType(String userType) {
        if (userType == null) {
            return 5; // Default to anonymous limit if userType is unknown
        }

        return switch (userType.toUpperCase()) {
            case "ADMIN" -> 100;
            case "REGISTERED" -> 20;
            case "ANONYMOUS" -> 5;
            default -> {
                log.warn("Unknown user type: {}, defaulting to ANONYMOUS limit", userType);
                yield 5;
            }
        };
    }
}
