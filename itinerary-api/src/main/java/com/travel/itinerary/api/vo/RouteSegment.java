package com.travel.itinerary.api.vo;

import java.math.BigDecimal;

/**
 * Immutable value object representing the routing result between two
 * consecutive itinerary steps.
 *
 * @param distanceKm       road / path distance in kilometres; never {@code null}
 * @param durationMin      estimated travel time in minutes; never {@code null}
 * @param geoJsonGeometry  GeoJSON {@code LineString} geometry string describing
 *                         the route polyline; may be {@code null} if the routing
 *                         engine does not return geometry
 */
public record RouteSegment(
        BigDecimal distanceKm,
        Integer durationMin,
        String geoJsonGeometry
) {}
