package com.travel.itinerary.impl.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing an immutable entry in the itinerary audit-event log.
 * <p>
 * Once created, an {@code ItineraryEvent} is never modified.  The
 * {@code @PrePersist} callback sets {@code createdAt} so the value is always
 * populated, even if the caller forgets to set it explicitly.
 * <p>
 * Package-private by design: only classes in this package (and the persistence
 * adapter) interact with this entity directly.
 */
@Entity
@Table(name = "itinerary_events")
@Getter
@NoArgsConstructor
public class ItineraryEvent {

    // -------------------------------------------------------------------------
    // Identity
    // -------------------------------------------------------------------------

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    UUID id;

    // -------------------------------------------------------------------------
    // Association
    // -------------------------------------------------------------------------

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "itinerary_id", nullable = false, updatable = false)
    Itinerary itinerary;

    // -------------------------------------------------------------------------
    // Event payload
    // -------------------------------------------------------------------------

    /** Discriminator value; maps to {@link com.travel.itinerary.impl.domain.enums.EventType}. */
    @Column(name = "event_type", nullable = false, updatable = false, length = 50)
    String eventType;

    /** Arbitrary JSON object (stored as JSONB) with event-specific metadata. */
    @Column(name = "details", columnDefinition = "jsonb", updatable = false)
    String details;

    // -------------------------------------------------------------------------
    // Audit
    // -------------------------------------------------------------------------

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @PrePersist
    void onPersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Creates a new audit event ready for persistence.
     *
     * @param itinerary the owning itinerary aggregate; must not be {@code null}
     * @param eventType discriminator string; see {@link com.travel.itinerary.impl.domain.enums.EventType}
     * @param details   optional JSON details string (may be {@code null})
     * @return new, unsaved {@code ItineraryEvent}
     */
    static ItineraryEvent of(Itinerary itinerary, String eventType, String details) {
        var event = new ItineraryEvent();
        event.itinerary = itinerary;
        event.eventType = eventType;
        event.details = details;
        event.createdAt = Instant.now();
        return event;
    }
}
