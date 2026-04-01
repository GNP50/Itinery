package com.travel.itinerary.api.vo;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Immutable value object representing a WGS-84 geographic coordinate.
 * <p>
 * Bean Validation constraints are declared on the record components so that
 * instances can be validated via the standard {@code Validator} API.
 *
 * @param lat latitude in the range [-90, 90]; must not be {@code null}
 * @param lon longitude in the range [-180, 180]; must not be {@code null}
 */
public record GeoCoordinate(

        @NotNull(message = "Latitude must not be null")
        @DecimalMin(value = "-90.0", message = "Latitude must be >= -90")
        @DecimalMax(value = "90.0",  message = "Latitude must be <= 90")
        BigDecimal lat,

        @NotNull(message = "Longitude must not be null")
        @DecimalMin(value = "-180.0", message = "Longitude must be >= -180")
        @DecimalMax(value = "180.0",  message = "Longitude must be <= 180")
        BigDecimal lon

) {

    /**
     * Compact canonical constructor: validates that neither component is {@code null}
     * and that the values are within the legal WGS-84 range.
     */
    public GeoCoordinate {
        if (lat == null) {
            throw new IllegalArgumentException("Latitude must not be null");
        }
        if (lon == null) {
            throw new IllegalArgumentException("Longitude must not be null");
        }
        if (lat.compareTo(BigDecimal.valueOf(-90)) < 0 || lat.compareTo(BigDecimal.valueOf(90)) > 0) {
            throw new IllegalArgumentException("Latitude must be in the range [-90, 90], got: " + lat);
        }
        if (lon.compareTo(BigDecimal.valueOf(-180)) < 0 || lon.compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new IllegalArgumentException("Longitude must be in the range [-180, 180], got: " + lon);
        }
    }

    /**
     * Convenience factory method accepting primitive {@code double} values.
     *
     * @param lat latitude
     * @param lon longitude
     * @return a validated {@link GeoCoordinate}
     */
    public static GeoCoordinate of(double lat, double lon) {
        return new GeoCoordinate(BigDecimal.valueOf(lat), BigDecimal.valueOf(lon));
    }
}
