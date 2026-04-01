package com.travel.geo.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * DTOs for Points-of-Interest (POI) discovery around a coordinate.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface PoiDTO {

    /**
     * Inbound POI search request.
     *
     * @param lat          WGS-84 latitude of the search centre
     * @param lon          WGS-84 longitude of the search centre
     * @param radiusMeters search radius in metres; must be positive
     * @param category     OSM tag category filter (e.g. {@code "tourism"},
     *                     {@code "restaurant"}, {@code "museum"}); {@code null}
     *                     returns all categories
     */
    record Request(
            @NotNull(message = "lat must not be null")
            @DecimalMin(value = "-90.0",  message = "lat must be >= -90")
            @DecimalMax(value =  "90.0",  message = "lat must be <= 90")
            BigDecimal lat,

            @NotNull(message = "lon must not be null")
            @DecimalMin(value = "-180.0", message = "lon must be >= -180")
            @DecimalMax(value =  "180.0", message = "lon must be <= 180")
            BigDecimal lon,

            @Positive(message = "radiusMeters must be positive")
            int radiusMeters,

            String category
    ) {}

    /**
     * Outbound POI discovery response.
     *
     * @param pois ordered list of POIs by ascending distance; never {@code null},
     *             may be empty
     */
    record Response(List<PoiItem> pois) {
        public Response {
            pois = pois == null ? List.of() : List.copyOf(pois);
        }
    }

    /**
     * A single POI result.
     *
     * @param osmId    OpenStreetMap element ID; may be {@code null} for non-OSM sources
     * @param name     display name of the POI; may be {@code null}
     * @param category OSM top-level category (e.g. {@code "tourism"})
     * @param lat      WGS-84 latitude
     * @param lon      WGS-84 longitude
     * @param tags     raw OSM tags associated with this element; never {@code null},
     *                 may be empty
     */
    record PoiItem(
            Long osmId,
            String name,
            String category,
            BigDecimal lat,
            BigDecimal lon,
            Map<String, String> tags
    ) {
        public PoiItem {
            tags = tags == null ? Map.of() : Map.copyOf(tags);
        }
    }
}
