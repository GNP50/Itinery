package com.travel.user.api.port.inbound;

import com.travel.user.api.dto.AuthDTO;

import java.util.Optional;
import java.util.UUID;

/**
 * Inbound use-case port for authentication operations.
 * <p>
 * Defines all authentication-related operations including user registration,
 * login, anonymous session creation, and current user extraction.
 * Implemented by the user domain's AuthService.
 */
public interface AuthUseCase {

    /**
     * Registers a new user with email and password.
     *
     * @param email    unique email address
     * @param password plain-text password (will be BCrypt-hashed)
     * @param name     display name
     * @return JWT response with the new user's id and bearer token
     * @throws com.travel.user.impl.exception.AuthException if the email is already registered
     */
    AuthDTO.Response register(String email, String password, String name);

    /**
     * Validates credentials and issues a JWT.
     *
     * @param email    registered email
     * @param password plain-text password
     * @return JWT response
     * @throws com.travel.user.impl.exception.AuthException if credentials are invalid
     */
    AuthDTO.Response login(String email, String password);

    /**
     * Creates a new anonymous user and issues a JWT.
     * Each call generates a brand-new anonymous user identity.
     *
     * @return JWT response for the anonymous session
     */
    AuthDTO.Response createAnonymous();

    /**
     * Retrieves the currently authenticated user's UUID from the security context.
     * <p>
     * Returns {@link Optional#empty()} when:
     * <ul>
     *   <li>No authentication is present (anonymous request)</li>
     *   <li>The authentication does not contain a valid userId claim</li>
     * </ul>
     *
     * @return optional user UUID extracted from the current security context
     */
    Optional<UUID> getCurrentUserId();

    /**
     * Returns the current authentication status including user type.
     * <p>
     * Used to determine queue priority (registered users get higher priority)
     * and other authorization decisions.
     *
     * @return authentication status response containing userId, userType, and authenticated flag
     */
    AuthDTO.StatusResponse getStatus();
}
