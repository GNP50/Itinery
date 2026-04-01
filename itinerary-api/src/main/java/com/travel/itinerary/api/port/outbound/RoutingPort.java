package com.travel.itinerary.api.port.outbound;

import com.travel.itinerary.api.vo.RouteSegment;

/**
 * Outbound SPI: turn-by-turn routing between two geographic coordinates.
 * <p>
 * Implementations may integrate with OSRM, GraphHopper, Valhalla, or any
 * compatible routing engine.
 */
public interface RoutingPort {

    /**
     * Calculate the optimal route between two coordinates.
     *
     * @param fromLat    WGS-84 latitude of the origin
     * @param fromLon    WGS-84 longitude of the origin
     * @param toLat      WGS-84 latitude of the destination
     * @param toLon      WGS-84 longitude of the destination
     * @param travelMode routing profile, e.g. {@code "DRIVING"}, {@code "CYCLING"},
     *                   {@code "WALKING"}; must not be {@code null}
     * @return a {@link RouteSegment} containing distance, duration and GeoJSON
     *         geometry; never {@code null}
     * @throws RoutingException if no route can be found or the upstream engine
     *                          is unavailable
     */
    RouteSegment getRoute(double fromLat, double fromLon, double toLat, double toLon, String travelMode);

    /**
     * Unchecked exception thrown when a routing request cannot be completed.
     */
    class RoutingException extends RuntimeException {

        public RoutingException(String message) {
            super(message);
        }

        public RoutingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
