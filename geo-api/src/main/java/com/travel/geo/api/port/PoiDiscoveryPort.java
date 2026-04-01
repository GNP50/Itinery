package com.travel.geo.api.port;

import com.travel.geo.api.dto.PoiDTO;

/**
 * Outbound SPI: discovery of Points of Interest (POIs) around a coordinate.
 * <p>
 * Implementations may query the Overpass API (OpenStreetMap), Foursquare,
 * Google Places, or any compatible POI data source. Uses the typed DTO records
 * from this module to remain independent of internal value objects.
 */
public interface PoiDiscoveryPort {

    /**
     * Find points of interest within a radius around a coordinate.
     *
     * @param request the POI search request; must not be {@code null}
     * @return response containing an unmodifiable list of POIs ordered by
     *         ascending distance; never {@code null}, may be empty
     * @throws PoiDiscoveryException if the upstream data source is unavailable
     */
    PoiDTO.Response findPoi(PoiDTO.Request request);

    /**
     * Unchecked exception thrown when a POI discovery request cannot be completed.
     */
    class PoiDiscoveryException extends RuntimeException {

        public PoiDiscoveryException(String message) {
            super(message);
        }

        public PoiDiscoveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
