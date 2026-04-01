package com.travel.user.api.dto;

import java.util.UUID;

/**
 * Container interface grouping all authentication-related request/response DTOs.
 */
public interface AuthDTO {

    /**
     * Payload for user registration.
     *
     * @param email    user email address
     * @param password plain-text password (will be hashed server-side)
     * @param name     display name
     */
    record RegisterRequest(String email, String password, String name) {}

    /**
     * Payload for user login.
     *
     * @param email    registered email
     * @param password plain-text password
     */
    record LoginRequest(String email, String password) {}

    /**
     * Response returned after successful registration, login, or anonymous session creation.
     *
     * @param userId      the internal user UUID
     * @param accessToken JWT bearer token to include in subsequent {@code Authorization} headers
     * @param userType    {@code "REGISTERED"} or {@code "ANONYMOUS"}
     */
    record Response(UUID userId, String accessToken, String userType) {}

    /**
     * Lightweight auth-status snapshot returned by {@code GET /api/v1/auth/check}.
     *
     * @param userId          the user UUID if authenticated, {@code null} otherwise
     * @param userType        {@code "REGISTERED"}, {@code "ANONYMOUS"}, or {@code null}
     * @param isAuthenticated {@code true} when a valid JWT was presented
     */
    record StatusResponse(UUID userId, String userType, boolean isAuthenticated) {}
}
