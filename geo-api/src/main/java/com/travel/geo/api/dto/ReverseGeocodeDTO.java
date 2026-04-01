package com.travel.geo.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * DTOs for reverse-geocoding (coordinate pair to structured address).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface ReverseGeocodeDTO {

    /**
     * Inbound reverse-geocode request.
     *
     * @param lat WGS-84 latitude in the range [-90, 90]; must not be {@code null}
     * @param lon WGS-84 longitude in the range [-180, 180]; must not be {@code null}
     */
    record Request(
            @NotNull(message = "lat must not be null")
            @DecimalMin(value = "-90.0",  message = "lat must be >= -90")
            @DecimalMax(value =  "90.0",  message = "lat must be <= 90")
            BigDecimal lat,

            @NotNull(message = "lon must not be null")
            @DecimalMin(value = "-180.0", message = "lon must be >= -180")
            @DecimalMax(value =  "180.0", message = "lon must be <= 180")
            BigDecimal lon
    ) {}

    /**
     * Outbound reverse-geocode response.
     *
     * @param displayName human-readable full address resolved from the coordinate
     * @param lat         echoed-back WGS-84 latitude (may be snapped to nearest road)
     * @param lon         echoed-back WGS-84 longitude (may be snapped to nearest road)
     * @param city        resolved city name; may be {@code null}
     * @param province    resolved province / county; may be {@code null}
     * @param region      resolved region / state; may be {@code null}
     * @param country     resolved country name; may be {@code null}
     * @param countryCode ISO 3166-1 alpha-2 country code; may be {@code null}
     */
    record Response(
            String displayName,
            BigDecimal lat,
            BigDecimal lon,
            String city,
            String province,
            String region,
            String country,
            String countryCode
    ) {}
}
