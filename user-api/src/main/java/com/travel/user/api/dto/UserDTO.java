package com.travel.user.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Data Transfer Objects for the User bounded context.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDTO {

    private UserDTO() {}

    // -------------------------------------------------------------------------
    // User representation
    // -------------------------------------------------------------------------

    /**
     * Full user details returned by lookup operations.
     */
    @Builder
    public record UserResponse(
        UUID    id,
        String  sub,
        String  email,
        String  name,
        String  userType,
        Instant createdAt
    ) {}

    // -------------------------------------------------------------------------
    // Admin status
    // -------------------------------------------------------------------------

    /**
     * Admin status check response.
     */
    public record AdminStatusResponse(boolean isAdmin) {}

    // -------------------------------------------------------------------------
    // Paginated list
    // -------------------------------------------------------------------------

    /**
     * Paginated list of users.
     */
    @Builder
    public record PagedUsersResponse(
        List<UserResponse> users,
        long               total,
        int                offset,
        int                limit
    ) {}
}
