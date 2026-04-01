package com.travel.itinerary.api.port.outbound;

import com.travel.itinerary.api.dto.AdminDTO;
import com.travel.itinerary.api.dto.ItineraryDTO;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound SPI: persistence operations for the Itinerary aggregate.
 * <p>
 * Implementations may delegate to a relational database, a document store, or
 * any other persistence technology; the domain layer depends only on this
 * interface and never on a specific technology.
 *
 * @param <T> the aggregate root type (typically {@code Itinerary} domain entity)
 */
public interface ItineraryPersistencePort<T> {

    /**
     * Persist a new itinerary or replace an existing one (upsert semantics).
     *
     * @param itinerary aggregate to save; must not be {@code null}
     * @return the saved aggregate, potentially with server-assigned fields
     *         (e.g. {@code createdAt}, {@code updatedAt}) populated
     */
    T save(T itinerary);

    /**
     * Look up an itinerary by its UUID.
     *
     * @param id itinerary UUID; must not be {@code null}
     * @return an {@link Optional} containing the aggregate, or empty if not found
     */
    Optional<T> findById(UUID id);

    /**
     * Look up an itinerary by its UUID and verify the access token in one
     * atomic operation.
     *
     * @param id    itinerary UUID; must not be {@code null}
     * @param token access token to validate; must not be {@code null}
     * @return an {@link Optional} containing the aggregate if both the UUID and
     *         the token match, or empty otherwise
     */
    Optional<T> findByIdAndToken(UUID id, String token);

    /**
     * Retrieve all persisted itineraries, ordered by creation time descending.
     *
     * @return unmodifiable list; never {@code null}, may be empty
     */
    List<T> findAll();

    /**
     * Permanently remove the itinerary identified by {@code id}.
     *
     * @param id itinerary UUID; must not be {@code null}
     */
    void delete(UUID id);

    /**
     * Check whether an access token is already associated with any itinerary.
     *
     * @param token token string to look up; must not be {@code null}
     * @return {@code true} if the token is in use, {@code false} otherwise
     */
    boolean existsByToken(String token);

    // =========================================================================
    // DTO-based query operations (optimized projections)
    // =========================================================================

    /**
     * Retrieves an itinerary as a DTO response by ID only.
     * <p>
     * This is an optimized query that returns a DTO projection without
     * ownership checks. Used for admin access.
     *
     * @param id itinerary UUID
     * @return optional DTO response if found
     */
    Optional<ItineraryDTO.Response> findResponseById(UUID id);

    /**
     * Retrieves an itinerary as a DTO response by ID and user ownership.
     * <p>
     * This is an optimized query that returns a DTO projection instead of
     * loading the full aggregate. Used for authenticated user access.
     *
     * @param id itinerary UUID
     * @param userId user UUID (owner)
     * @return optional DTO response if found and owned by the user
     */
    Optional<ItineraryDTO.Response> findResponseByIdAndUser(UUID id, UUID userId);

    /**
     * Retrieves an itinerary as a DTO response by ID and access token.
     * <p>
     * This is an optimized query for anonymous/shared access.
     *
     * @param id itinerary UUID
     * @param token access token
     * @return optional DTO response if found and token matches
     */
    Optional<ItineraryDTO.Response> findResponseByIdAndToken(UUID id, String token);

    /**
     * Retrieves all itineraries owned by a specific user.
     * <p>
     * Returns full domain aggregates (not DTOs) for business logic operations.
     *
     * @param userId user UUID
     * @return list of itineraries owned by the user, may be empty
     */
    List<T> findByUserId(UUID userId);

    /**
     * Retrieves all itineraries owned by a specific user with a specific status.
     * <p>
     * Performance-optimized query for filtering by both owner and status.
     * Used for queue status queries to avoid loading all queued items.
     *
     * @param userId user UUID
     * @param status itinerary status (enum will be passed as Object to maintain generic interface)
     * @return list of itineraries matching both userId and status, may be empty
     */
    List<T> findByOwnerIdAndStatus(UUID userId, Object status);

    // =========================================================================
    // Admin operations
    // =========================================================================

    /**
     * Returns a paginated list of all itineraries with owner information.
     * <p>
     * This is an admin-only operation that returns itinerary summaries with
     * owner details from the user table.
     *
     * @param page zero-based page number
     * @param size number of items per page
     * @return paginated response with itinerary summaries and owner information
     */
    AdminDTO.PagedItineraries findAllItinerariesWithOwner(int page, int size);
}
