package com.travel.geo.api.port;

import com.travel.geo.api.dto.RouteDTO;

/**
 * Outbound SPI: point-to-point routing between two geographic coordinates.
 * <p>
 * Implementations may integrate with OSRM, GraphHopper, Valhalla, or any
 * compatible routing engine. Uses the typed DTO records from this module
 * to remain independent of internal value objects.
 */
public interface RoutingPort {

    /**
     * Calculate the optimal route between two coordinates.
     *
     * @param request the routing request; must not be {@code null}
     * @return the route result with distance, duration and optional GeoJSON geometry;
     *         never {@code null}
     * @throws RoutingException if no route can be found or the upstream engine
     *                          is unavailable
     */
    RouteDTO.Response getRoute(RouteDTO.Request request);

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
