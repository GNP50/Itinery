package com.travel.geo.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTOs for forward geo-search (free-text to a ranked list of candidates).
 * <p>
 * All inner types are Java 21 records to guarantee immutability and provide
 * automatic equals/hashCode/toString without Lombok.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface GeoSearchDTO {

    /**
     * Inbound search request.
     *
     * @param query       free-text search string; must not be blank
     * @param countryCode optional ISO 3166-1 alpha-2 filter (e.g. {@code "IT"});
     *                    {@code null} means global search
     * @param limit       maximum number of results to return (1–50)
     */
    record Request(
            @NotBlank(message = "query must not be blank")
            String query,

            String countryCode,

            @Min(value = 1, message = "limit must be at least 1")
            @Max(value = 50, message = "limit must not exceed 50")
            int limit
    ) {
        /** Compact constructor – apply defaults for optional fields. */
        public Request {
            if (limit <= 0) {
                limit = 10;
            }
        }
    }

    /**
     * Outbound search response wrapping an ordered list of candidates.
     *
     * @param results ranked list of geo results; never {@code null}, may be empty
     */
    record Response(List<GeoResult> results) {
        public Response {
            results = results == null ? List.of() : List.copyOf(results);
        }
    }

    /**
     * A single geo-search candidate.
     *
     * @param displayName human-readable full address
     * @param lat         WGS-84 latitude
     * @param lon         WGS-84 longitude
     * @param osmId       OpenStreetMap element ID; may be {@code null} for
     *                    non-OSM sources
     * @param city        resolved city name; may be {@code null}
     * @param province    resolved province / county; may be {@code null}
     * @param region      resolved region / state; may be {@code null}
     * @param country     resolved country name; may be {@code null}
     * @param countryCode ISO 3166-1 alpha-2 country code; may be {@code null}
     * @param type        OSM element type (e.g. {@code "node"}, {@code "way"},
     *                    {@code "relation"}); may be {@code null}
     */
    record GeoResult(
            String displayName,
            BigDecimal lat,
            BigDecimal lon,
            Long osmId,
            String city,
            String province,
            String region,
            String country,
            String countryCode,
            String type
    ) {}
}
