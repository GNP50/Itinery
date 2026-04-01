package com.travel.itinerary.impl.domain;

import com.travel.itinerary.impl.domain.enums.ItineraryStatus;
import com.travel.itinerary.impl.domain.enums.TravelMode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity and aggregate root representing a travel itinerary.
 * <p>
 * Package-private by design: only classes within this package and the
 * dedicated persistence adapter interact with this entity directly.  The rest
 * of the application works exclusively through
 * {@link com.travel.itinerary.api.dto.ItineraryDTO} and the port interfaces.
 *
 * <h3>Access-token generation</h3>
 * A cryptographically random 32-byte token is generated in {@link #onPrePersist()}
 * and stored with the prefix {@code "tok_"} to make tokens easily identifiable
 * in logs and request headers.
 *
 * <h3>Optimistic locking</h3>
 * The {@code @Version} field prevents lost-update anomalies when multiple
 * requests concurrently modify the same aggregate.
 */
@Entity
@Table(
    name = "itineraries",
    indexes = {
        @Index(name = "idx_itineraries_access_token", columnList = "access_token", unique = true),
        @Index(name = "idx_itineraries_status",       columnList = "status"),
        @Index(name = "idx_itineraries_user_id",      columnList = "user_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class Itinerary {

    // -------------------------------------------------------------------------
    // Shared SecureRandom – thread-safe per JDK specification
    // -------------------------------------------------------------------------
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_URL  = Base64.getUrlEncoder().withoutPadding();
    private static final int TOKEN_BYTES            = 32;
    private static final String TOKEN_PREFIX        = "tok_";

    // -------------------------------------------------------------------------
    // Identity
    // -------------------------------------------------------------------------

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    UUID id;

    @Column(name = "access_token", nullable = false, unique = true, updatable = false, length = 64)
    String accessToken;

    /** UUID of the owning user (registered or anonymous). Set at creation time. */
    @Column(name = "user_id", nullable = false)
    UUID userId;

    // -------------------------------------------------------------------------
    // Core fields
    // -------------------------------------------------------------------------

    @Column(name = "title", nullable = false, length = 255)
    String title;

    @Column(name = "description", columnDefinition = "TEXT")
    String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    ItineraryStatus status = ItineraryStatus.QUEUED;

    @Column(name = "queue_position")
    Integer queuePosition;

    @Column(name = "estimated_completion")
    Instant estimatedCompletion;

    @Enumerated(EnumType.STRING)
    @Column(name = "travel_mode", nullable = false, length = 20)
    TravelMode travelMode;

    // -------------------------------------------------------------------------
    // JSON / JSONB blobs
    // -------------------------------------------------------------------------

    /** User preferences serialised as a JSON object string (stored as JSONB). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferences", columnDefinition = "jsonb")
    String preferences;

    /** AI-generated suggestions for the whole itinerary, stored as JSONB. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_suggestions", columnDefinition = "jsonb")
    String aiSuggestions;

    // -------------------------------------------------------------------------
    // Computed metrics (populated after processing)
    // -------------------------------------------------------------------------

    @Column(name = "total_distance_km", precision = 12, scale = 3)
    BigDecimal totalDistanceKm;

    @Column(name = "total_duration_minutes")
    Integer totalDurationMinutes;

    @Column(name = "current_step_index", nullable = false)
    Integer currentStepIndex = 0;

    // -------------------------------------------------------------------------
    // Optimistic locking
    // -------------------------------------------------------------------------

    @Version
    @Column(name = "version", nullable = false)
    Integer version = 0;

    // -------------------------------------------------------------------------
    // Audit timestamps
    // -------------------------------------------------------------------------

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    // -------------------------------------------------------------------------
    // Child collection
    // -------------------------------------------------------------------------

    @OneToMany(
        mappedBy     = "itinerary",
        cascade      = CascadeType.ALL,
        orphanRemoval = true,
        fetch        = FetchType.LAZY
    )
    @OrderBy("stepOrder ASC")
    List<ItineraryStep> steps = new ArrayList<>();

    // -------------------------------------------------------------------------
    // JPA lifecycle callbacks
    // -------------------------------------------------------------------------

    @PrePersist
    void onPrePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;

        // Generate a URL-safe, 32-byte random access token prefixed with "tok_"
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(tokenBytes);
        accessToken = TOKEN_PREFIX + BASE64_URL.encodeToString(tokenBytes);
    }

    @PreUpdate
    void onPreUpdate() {
        updatedAt = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Step management helpers
    // -------------------------------------------------------------------------

    /**
     * Appends a step to this itinerary and establishes the bi-directional link.
     *
     * @param step the step to add; must not be {@code null}
     */
    public void addStep(ItineraryStep step) {
        step.itinerary = this;
        steps.add(step);
    }

    /**
     * Removes all existing steps, replacing them with the provided list.
     * Triggers orphan-removal cascade on the next flush.
     *
     * @param newSteps replacement list; must not be {@code null}
     */
    public void replaceSteps(List<ItineraryStep> newSteps) {
        steps.clear();
        newSteps.forEach(this::addStep);
    }

    // -------------------------------------------------------------------------
    // Static factory
    // -------------------------------------------------------------------------

    /**
     * Creates and returns a new, unsaved {@code Itinerary} aggregate with
     * QUEUED status and no steps.
     *
     * @param title       human-readable title; must not be blank
     * @param description optional free-text description (may be {@code null})
     * @param travelMode  mode of transport; must not be {@code null}
     * @param preferences user preferences serialised as a JSON string (may be {@code null})
     * @return a new, transient {@code Itinerary} instance
     */
    public static Itinerary create(String title, String description, TravelMode travelMode,
                                    String preferences, UUID userId) {
        var itinerary = new Itinerary();
        itinerary.title       = title;
        itinerary.description = description;
        itinerary.travelMode  = travelMode;
        itinerary.preferences = preferences;
        itinerary.status      = ItineraryStatus.QUEUED;
        itinerary.userId      = userId;
        return itinerary;
    }
}
