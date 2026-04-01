package com.travel.itinerary.impl.adapter.outbound.persistence;

import com.travel.itinerary.api.dto.AdminDTO;
import com.travel.itinerary.impl.domain.Itinerary;
import com.travel.itinerary.impl.domain.enums.ItineraryStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link Itinerary} aggregate root.
 * <p>
 * Package-private: only {@link ItineraryPersistenceAdapter} in this package
 * should interact with this interface; the domain layer depends solely on
 * {@link com.travel.itinerary.api.port.outbound.ItineraryPersistencePort}.
 */
interface ItineraryJpaRepository extends JpaRepository<Itinerary, UUID> {

    /**
     * Fetches an itinerary by its primary key while also verifying the access
     * token in a single database round-trip.
     *
     * @param id          itinerary UUID
     * @param accessToken bearer token to validate
     * @return optional itinerary if both fields match
     */
    Optional<Itinerary> findByIdAndAccessToken(UUID id, String accessToken);

    /**
     * Looks up an itinerary solely by its access token.
     *
     * @param token bearer token
     * @return optional itinerary
     */
    Optional<Itinerary> findByAccessToken(String token);

    /**
     * Returns all itineraries with the given status, ordered by creation time
     * ascending (oldest first – natural queue order), with steps eagerly loaded.
     *
     * @param status lifecycle status to filter on
     * @return ordered list with steps loaded, never {@code null}
     */
    @Query("SELECT DISTINCT i FROM Itinerary i LEFT JOIN FETCH i.steps WHERE i.status = :status ORDER BY i.createdAt ASC")
    List<Itinerary> findByStatus(@Param("status") ItineraryStatus status);

    /**
     * Checks whether an access token is already in use without loading the full
     * entity.
     *
     * @param token token string to verify
     * @return {@code true} if any itinerary holds this token
     */
    boolean existsByAccessToken(String token);

    /**
     * Fetches an itinerary by its primary key and owning user UUID.
     * Used for JWT-authenticated access control.
     *
     * @param id     itinerary UUID
     * @param userId owning user UUID
     * @return optional itinerary if both fields match
     */
    Optional<Itinerary> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Returns all itineraries owned by the given user with steps eagerly loaded.
     * <p>
     * Uses JOIN FETCH to avoid N+1 queries: 1 query instead of 1 + N.
     *
     * @param userId owning user UUID
     * @return list of itineraries with all steps loaded, never {@code null}
     */
    @Query("SELECT DISTINCT i FROM Itinerary i LEFT JOIN FETCH i.steps WHERE i.userId = :userId ORDER BY i.createdAt DESC")
    java.util.List<Itinerary> findByUserId(@Param("userId") UUID userId);

    /**
     * Returns all itineraries owned by the given user with a specific status and steps eagerly loaded.
     * Used for performance-optimized queue status queries.
     * <p>
     * Uses JOIN FETCH to avoid N+1 queries.
     *
     * @param userId owning user UUID
     * @param status lifecycle status to filter on
     * @return list of itineraries matching both userId and status with steps loaded, never {@code null}
     */
    @Query("SELECT DISTINCT i FROM Itinerary i LEFT JOIN FETCH i.steps WHERE i.userId = :userId AND i.status = :status ORDER BY i.createdAt DESC")
    java.util.List<Itinerary> findByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") ItineraryStatus status);

    /**
     * Fetches a paginated list of all itineraries with their owner information.
     * Used for admin dashboard to display all itineraries with user details.
     *
     * @param pageable pagination parameters
     * @return list of itinerary summaries with owner data
     */
    @Query(value = """
        SELECT
            i.id               AS id,
            i.title            AS title,
            i.status           AS status,
            i.user_id          AS userId,
            u.email            AS email,
            u.name             AS name,
            u.user_type        AS userType,
            i.created_at       AS createdAt,
            i.access_token     AS accessToken
        FROM itineraries i
        LEFT JOIN users u ON u.id = i.user_id
        ORDER BY i.created_at DESC
        LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}
        """, nativeQuery = true)
    List<Object[]> findAllWithOwnerNative(Pageable pageable);

    /**
     * Counts the total number of itineraries in the database.
     *
     * @return total count of itineraries
     */
    @Query("SELECT COUNT(i) FROM Itinerary i")
    long countAllItineraries();

    /**
     * Finds a single itinerary step by its ID (searches across all itineraries).
     * <p>
     * Uses a native query for efficiency - avoids loading full itineraries.
     *
     * @param stepId the step UUID to find
     * @return optional step
     */
    @Query(value = """
        SELECT * FROM itinerary_steps WHERE id = :stepId LIMIT 1
        """, nativeQuery = true)
    Optional<com.travel.itinerary.impl.domain.ItineraryStep> findStepById(@Param("stepId") UUID stepId);

    /**
     * Returns all itineraries with steps eagerly loaded.
     * <p>
     * Uses JOIN FETCH to avoid N+1 queries: 1 query instead of 1 + N.
     *
     * @return list of all itineraries with steps loaded, never {@code null}
     */
    @Query("SELECT DISTINCT i FROM Itinerary i LEFT JOIN FETCH i.steps ORDER BY i.createdAt DESC")
    List<Itinerary> findAllWithSteps();
}
