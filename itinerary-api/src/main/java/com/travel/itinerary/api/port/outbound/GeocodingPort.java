package com.travel.itinerary.api.port.outbound;

import com.travel.itinerary.api.vo.GeoResult;

import java.util.List;

/**
 * Outbound SPI: geocoding and reverse-geocoding operations.
 * <p>
 * Implementations may integrate with Nominatim, Google Maps, HERE, or any
 * compatible geocoding service.
 */
public interface GeocodingPort {

    /**
     * Forward-geocode a free-text place name and return the single best match.
     * Used internally during itinerary processing where one canonical result is needed.
     *
     * @param query       free-text search string (e.g. {@code "Colosseum, Rome"});
     *                    must not be {@code null} or blank
     * @param countryCode ISO 3166-1 alpha-2 country code used to bias results
     *                    (e.g. {@code "IT"}); may be {@code null} to search globally
     * @return the best-matching {@link GeoResult}; never {@code null}
     * @throws GeocodingException if the place cannot be resolved or the upstream
     *                            service is unavailable
     */
    GeoResult geocode(String query, String countryCode);

    /**
     * Search for places matching the query and return up to {@code limit} candidates.
     * Used by the autocomplete endpoint where multiple suggestions are desirable.
     *
     * <p>Default implementation wraps {@link #geocode} in a single-element list so
     * existing adapters remain compatible without change.
     *
     * @param query       free-text search string; must not be {@code null} or blank
     * @param countryCode ISO 3166-1 alpha-2 country code bias; may be {@code null}
     * @param limit       maximum number of results to return (advisory)
     * @return ordered list of matching {@link GeoResult}s; never {@code null}, may be empty
     */
    default List<GeoResult> searchPlaces(String query, String countryCode, int limit) {
        return List.of(geocode(query, countryCode));
    }

    /**
     * Reverse-geocode a WGS-84 coordinate pair to a structured address.
     *
     * @param lat WGS-84 latitude
     * @param lon WGS-84 longitude
     * @return the resolved {@link GeoResult}; never {@code null}
     * @throws GeocodingException if the coordinate cannot be resolved or the
     *                            upstream service is unavailable
     */
    GeoResult reverseGeocode(double lat, double lon);

    /**
     * Unchecked exception thrown when a geocoding operation cannot be completed.
     */
    class GeocodingException extends RuntimeException {

        public GeocodingException(String message) {
            super(message);
        }

        public GeocodingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
