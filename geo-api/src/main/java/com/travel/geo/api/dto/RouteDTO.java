package com.travel.geo.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * DTOs for point-to-point routing requests and responses.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface RouteDTO {

    /**
     * Inbound routing request.
     *
     * @param fromLat    WGS-84 latitude of the origin
     * @param fromLon    WGS-84 longitude of the origin
     * @param toLat      WGS-84 latitude of the destination
     * @param toLon      WGS-84 longitude of the destination
     * @param travelMode routing profile, e.g. {@code "DRIVING"}, {@code "CYCLING"},
     *                   {@code "WALKING"}; must not be blank
     */
    record Request(
            @NotNull(message = "fromLat must not be null")
            @DecimalMin(value = "-90.0",  message = "fromLat must be >= -90")
            @DecimalMax(value =  "90.0",  message = "fromLat must be <= 90")
            BigDecimal fromLat,

            @NotNull(message = "fromLon must not be null")
            @DecimalMin(value = "-180.0", message = "fromLon must be >= -180")
            @DecimalMax(value =  "180.0", message = "fromLon must be <= 180")
            BigDecimal fromLon,

            @NotNull(message = "toLat must not be null")
            @DecimalMin(value = "-90.0",  message = "toLat must be >= -90")
            @DecimalMax(value =  "90.0",  message = "toLat must be <= 90")
            BigDecimal toLat,

            @NotNull(message = "toLon must not be null")
            @DecimalMin(value = "-180.0", message = "toLon must be >= -180")
            @DecimalMax(value =  "180.0", message = "toLon must be <= 180")
            BigDecimal toLon,

            @NotBlank(message = "travelMode must not be blank")
            String travelMode
    ) {}

    /**
     * Outbound routing response.
     *
     * @param distanceKm       total route distance in kilometres
     * @param durationMinutes  estimated travel duration in minutes
     * @param geoJsonGeometry  GeoJSON LineString geometry of the route path;
     *                         may be {@code null} when the routing engine does not
     *                         return geometry
     * @param fromFallback     {@code true} when the result was produced by a fallback
     *                         engine (e.g. straight-line distance) rather than the
     *                         primary routing service
     */
    record Response(
            BigDecimal distanceKm,
            Integer durationMinutes,
            String geoJsonGeometry,
            boolean fromFallback
    ) {}
}
