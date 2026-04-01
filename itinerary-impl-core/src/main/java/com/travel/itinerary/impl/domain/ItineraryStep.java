package com.travel.itinerary.impl.domain;

import com.travel.itinerary.impl.domain.enums.StepStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA entity representing a single ordered stop within an {@link Itinerary}.
 * <p>
 * Package-private by design: only classes in this package and the persistence
 * adapter are permitted to construct or mutate step instances directly.
 */
@Entity
@Table(name = "itinerary_steps")
@Getter
@Setter
@NoArgsConstructor
public class ItineraryStep {

    // -------------------------------------------------------------------------
    // Identity
    // -------------------------------------------------------------------------

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    UUID id;

    // -------------------------------------------------------------------------
    // Ordering
    // -------------------------------------------------------------------------

    @Column(name = "step_order", nullable = false)
    int stepOrder;

    // -------------------------------------------------------------------------
    // Location fields
    // -------------------------------------------------------------------------

    @Column(name = "place_name")
    String placeName;

    @Column(name = "city")
    String city;

    @Column(name = "province")
    String province;

    @Column(name = "region")
    String region;

    @Column(name = "country")
    String country;

    @Column(name = "country_code", columnDefinition = "CHAR(2)")
    String countryCode;

    @Column(name = "latitude", precision = 10, scale = 7)
    BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    BigDecimal longitude;

    @Column(name = "osm_id")
    Long osmId;

    // -------------------------------------------------------------------------
    // Traveller & AI content
    // -------------------------------------------------------------------------

    @Column(name = "notes", columnDefinition = "TEXT")
    String notes;

    @Column(name = "ai_description", columnDefinition = "TEXT")
    String aiDescription;

    @Column(name = "ai_tips", columnDefinition = "TEXT")
    String aiTips;

    // -------------------------------------------------------------------------
    // Routing metrics
    // -------------------------------------------------------------------------

    @Column(name = "distance_from_prev_km", precision = 10, scale = 3)
    BigDecimal distanceFromPrevKm;

    @Column(name = "duration_from_prev_min")
    Integer durationFromPrevMin;

    /** GeoJSON LineString geometry from previous step, stored as JSONB. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "route_geometry_from_prev", columnDefinition = "jsonb")
    String routeGeometryFromPrev;

    /** JSON array of nearby points of interest. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "poi_nearby", columnDefinition = "jsonb")
    String poiNearby;

    // -------------------------------------------------------------------------
    // Preferences
    // -------------------------------------------------------------------------

    /** Step-level preferences override trip-level preferences (stored as JSONB). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferences", columnDefinition = "jsonb")
    String preferences;

    // -------------------------------------------------------------------------
    // Status & temporal
    // -------------------------------------------------------------------------

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    StepStatus status = StepStatus.PENDING;

    @Column(name = "arrival_date")
    LocalDate arrivalDate;

    // -------------------------------------------------------------------------
    // Association back to parent
    // -------------------------------------------------------------------------

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "itinerary_id", nullable = false)
    Itinerary itinerary;

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Creates a new step with the minimum required fields.
     *
     * @param stepOrder   1-based position in the itinerary
     * @param placeName   free-text name of the place (used for geocoding)
     * @param notes       optional traveller notes
     * @param arrivalDate optional planned arrival date
     * @return a new, unsaved {@code ItineraryStep}
     */
    public static ItineraryStep create(int stepOrder, String placeName, String notes, LocalDate arrivalDate) {
        var step = new ItineraryStep();
        step.stepOrder = stepOrder;
        step.placeName = placeName;
        step.notes = notes;
        step.arrivalDate = arrivalDate;
        step.status = StepStatus.PENDING;
        return step;
    }
}
