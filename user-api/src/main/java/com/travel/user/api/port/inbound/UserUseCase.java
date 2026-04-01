package com.travel.user.api.port.inbound;

import com.travel.user.api.dto.UserDTO;

import java.util.Optional;
import java.util.UUID;

/**
 * Inbound use-case port for the User domain.
 * Implemented by the service layer in user-impl.
 */
public interface UserUseCase {

    /**
     * Find a user by their subject identifier (email for registered, anon_* for anonymous).
     */
    Optional<UserDTO.UserResponse> findBySub(String sub);

    /**
     * Find a user by their internal UUID.
     */
    Optional<UserDTO.UserResponse> findById(UUID id);

    /**
     * List all users with pagination.
     *
     * @param offset zero-based row offset
     * @param limit  max rows to return
     */
    UserDTO.PagedUsersResponse listUsers(int offset, int limit);

    /**
     * Check if the given user has admin (REGISTERED) status.
     */
    boolean isAdmin(UUID userId);
}
