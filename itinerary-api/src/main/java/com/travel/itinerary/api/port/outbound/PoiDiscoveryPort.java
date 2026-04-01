package com.travel.itinerary.api.port.outbound;

import com.travel.itinerary.api.vo.PoiResult;

import java.util.List;

/**
 * Outbound SPI: discovery of points of interest (POIs) around a coordinate.
 * <p>
 * Implementations may query Overpass API (OpenStreetMap), Foursquare, Google
 * Places, or any compatible POI data source.
 */
public interface PoiDiscoveryPort {

    /**
     * Find points of interest within a radius around a coordinate.
     *
     * @param lat          WGS-84 latitude of the search centre
     * @param lon          WGS-84 longitude of the search centre
     * @param radiusMeters search radius in metres; must be positive
     * @param category     OSM tag category filter (e.g. {@code "tourism"},
     *                     {@code "restaurant"}, {@code "museum"}); may be
     *                     {@code null} to return all categories
     * @return unmodifiable list of {@link PoiResult}s ordered by distance
     *         ascending; never {@code null}, may be empty
     * @throws PoiDiscoveryException if the upstream data source is unavailable
     */
    List<PoiResult> findPoi(double lat, double lon, int radiusMeters, String category);

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
