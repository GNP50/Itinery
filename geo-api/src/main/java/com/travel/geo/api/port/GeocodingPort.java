package com.travel.geo.api.port;

import com.travel.geo.api.dto.GeoSearchDTO;
import com.travel.geo.api.dto.ReverseGeocodeDTO;

/**
 * Outbound SPI: forward and reverse geocoding operations.
 * <p>
 * Implementations may integrate with Nominatim, Google Maps, HERE, or any
 * compatible geocoding service. This port is the geo-api counterpart of the
 * itinerary-api {@code GeocodingPort} and uses the typed DTO records defined
 * in this module to decouple callers from internal value objects.
 */
public interface GeocodingPort {

    /**
     * Forward-geocode a free-text query and return ranked candidates.
     *
     * @param request the search request; must not be {@code null}
     * @return response containing an ordered list of candidates; never {@code null}
     * @throws GeocodingException if the upstream service is unavailable or no
     *                            results can be produced
     */
    GeoSearchDTO.Response search(GeoSearchDTO.Request request);

    /**
     * Reverse-geocode a WGS-84 coordinate pair to a structured address.
     *
     * @param request the reverse-geocode request; must not be {@code null}
     * @return the resolved address; never {@code null}
     * @throws GeocodingException if the upstream service is unavailable or the
     *                            coordinate cannot be resolved
     */
    ReverseGeocodeDTO.Response reverseGeocode(ReverseGeocodeDTO.Request request);

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
