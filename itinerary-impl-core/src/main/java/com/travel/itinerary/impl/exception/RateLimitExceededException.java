package com.travel.itinerary.impl.exception;

import lombok.Getter;

/**
 * Exception thrown when a user exceeds their rate limit for API requests.
 *
 * <p>This exception maps to HTTP 429 (Too Many Requests) and includes metadata
 * about the rate limit violation to inform the client when they can retry.</p>
 *
 * <h3>Response Format:</h3>
 * <pre>{@code
 * {
 *   "status": 429,
 *   "error": "Too Many Requests",
 *   "message": "Rate limit exceeded: 6/5 requests per 3600 seconds",
 *   "retryAfter": 3425,
 *   "userType": "ANONYMOUS",
 *   "limit": 5,
 *   "currentCount": 6,
 *   "window": 3600
 * }
 * }</pre>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * if (requestCount > limit) {
 *     throw new RateLimitExceededException(
 *         "Rate limit exceeded: 6/5 requests per hour",
 *         3425,  // retry after 57 minutes
 *         5,     // limit
 *         6,     // current count
 *         "ANONYMOUS"
 *     );
 * }
 * }</pre>
 *
 * @since 1.0
 */
@Getter
public class RateLimitExceededException extends RuntimeException {

    /**
     * Number of seconds until the rate limit window resets.
     * Clients should wait at least this long before retrying.
     */
    private final long retryAfter;

    /**
     * The maximum number of requests allowed in the window.
     */
    private final int limit;

    /**
     * The current request count (which exceeded the limit).
     */
    private final int currentCount;

    /**
     * The user type that triggered the rate limit ("ADMIN", "REGISTERED", "ANONYMOUS").
     */
    private final String userType;

    /**
     * Creates a new rate limit exceeded exception.
     *
     * @param message human-readable error message
     * @param retryAfter seconds until the rate limit resets
     * @param limit the maximum requests allowed
     * @param currentCount the current request count
     * @param userType the user type
     */
    public RateLimitExceededException(
            String message,
            long retryAfter,
            int limit,
            int currentCount,
            String userType
    ) {
        super(message);
        this.retryAfter = retryAfter;
        this.limit = limit;
        this.currentCount = currentCount;
        this.userType = userType;
    }

    /**
     * Creates a new rate limit exceeded exception with default values.
     *
     * @param message human-readable error message
     * @param retryAfter seconds until the rate limit resets
     */
    public RateLimitExceededException(String message, long retryAfter) {
        this(message, retryAfter, -1, -1, "UNKNOWN");
    }

    @Override
    public String toString() {
        return String.format(
                "RateLimitExceededException{message='%s', userType='%s', limit=%d, currentCount=%d, retryAfter=%ds}",
                getMessage(),
                userType,
                limit,
                currentCount,
                retryAfter
        );
    }
}
