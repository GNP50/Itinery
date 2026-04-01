package com.travel.itinerary.impl.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Method-level annotation that enables Redis-backed sliding-window rate limiting.
 * <p>
 * Methods annotated with {@code @RateLimit} are intercepted by
 * {@link com.travel.itinerary.impl.aspect.RateLimitAspect}.  If the caller
 * exceeds the configured request limit within the time window a
 * {@link com.travel.itinerary.impl.exception.QueueFullException} with HTTP 429 is
 * thrown.
 *
 * <h3>Example usage</h3>
 * <pre>{@code
 * @RateLimit(requests = 5, windowSeconds = 60, key = "geocode")
 * public GeoResult geocode(String query, String countryCode) { ... }
 * }</pre>
 *
 * <p>The Redis key used for counting is constructed as:
 * {@code rate_limit:<key>:<windowTimestamp>}
 * where {@code windowTimestamp} is the current epoch-second divided by
 * {@code windowSeconds} (i.e. a fixed-window bucket).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * Maximum number of requests allowed within the {@link #windowSeconds()} window.
     *
     * @return request limit (default 10)
     */
    int requests() default 10;

    /**
     * Duration of the sliding-window in seconds.
     *
     * @return window size in seconds (default 60)
     */
    int windowSeconds() default 60;

    /**
     * Logical identifier used to namespace the Redis counter key.
     * <p>
     * If left blank the aspect will derive a key from the fully-qualified
     * method name ({@code ClassName.methodName}).
     *
     * @return key prefix (default empty string)
     */
    String key() default "";
}
