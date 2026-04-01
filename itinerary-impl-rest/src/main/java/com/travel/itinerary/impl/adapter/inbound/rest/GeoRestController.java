package com.travel.itinerary.impl.adapter.inbound.rest;

import com.travel.itinerary.api.port.outbound.GeocodingPort;
import com.travel.itinerary.api.port.outbound.PoiDiscoveryPort;
import com.travel.itinerary.api.port.outbound.RoutingPort;
import com.travel.itinerary.api.vo.GeoResult;
import com.travel.itinerary.api.vo.PoiResult;
import com.travel.itinerary.api.vo.RouteSegment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


/**
 * REST adapter providing direct access to the geo-services (geocoding,
 * reverse-geocoding, routing, and POI discovery) for testing and direct
 * client consumption.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/geo")
@RequiredArgsConstructor
@Tag(name = "Geo", description = "Geocoding, routing and points-of-interest operations")
public class GeoRestController {

    private final GeocodingPort    geocodingPort;
    private final RoutingPort      routingPort;
    private final PoiDiscoveryPort poiDiscoveryPort;

    // -------------------------------------------------------------------------
    // GET /search  – forward geocoding
    // -------------------------------------------------------------------------

    @Operation(
        summary     = "Forward geocode a place name",
        description = "Resolves a free-text place query to WGS-84 coordinates and structured address components."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Geocoding result"),
        @ApiResponse(responseCode = "502", description = "Upstream geocoding service unavailable",
                     content = @Content(schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class)))
    })
    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<GeoResult>> search(
            @Parameter(description = "Free-text place name (e.g. \"Colosseum, Rome\")", required = true)
            @RequestParam("q") String q,
            @Parameter(description = "ISO 3166-1 alpha-2 country code to bias results (optional)")
            @RequestParam(value = "country_code", required = false) String countryCode,
            @Parameter(description = "Maximum number of results (default 5, max 10)")
            @RequestParam(value = "limit", defaultValue = "5") int limit) {

        log.debug("GET /api/v1/geo/search q='{}' countryCode={} limit={}", q, countryCode, limit);
        List<GeoResult> results = geocodingPort.searchPlaces(q, countryCode, Math.min(limit, 10));
        return ResponseEntity.ok(results);
    }

    // -------------------------------------------------------------------------
    // GET /reverse  – reverse geocoding
    // -------------------------------------------------------------------------

    @Operation(
        summary     = "Reverse geocode coordinates",
        description = "Resolves a WGS-84 coordinate pair to a structured address."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Reverse geocoding result"),
        @ApiResponse(responseCode = "502", description = "Upstream geocoding service unavailable",
                     content = @Content(schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class)))
    })
    @GetMapping(value = "/reverse", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GeoResult> reverse(
            @Parameter(description = "WGS-84 latitude", required = true)
            @RequestParam("lat") double lat,
            @Parameter(description = "WGS-84 longitude", required = true)
            @RequestParam("lon") double lon) {

        log.debug("GET /api/v1/geo/reverse lat={} lon={}", lat, lon);
        GeoResult result = geocodingPort.reverseGeocode(lat, lon);
        return ResponseEntity.ok(result);
    }

    // -------------------------------------------------------------------------
    // GET /route  – routing
    // -------------------------------------------------------------------------

    @Operation(
        summary     = "Calculate a route between two coordinates",
        description = "Returns distance, duration, and GeoJSON LineString geometry for the optimal route "
                    + "between the origin and destination coordinates."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Route calculated"),
        @ApiResponse(responseCode = "502", description = "Upstream routing engine unavailable",
                     content = @Content(schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class)))
    })
    @GetMapping(value = "/route", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RouteSegment> route(
            @Parameter(description = "Origin latitude", required = true)
            @RequestParam("from_lat") double fromLat,
            @Parameter(description = "Origin longitude", required = true)
            @RequestParam("from_lon") double fromLon,
            @Parameter(description = "Destination latitude", required = true)
            @RequestParam("to_lat") double toLat,
            @Parameter(description = "Destination longitude", required = true)
            @RequestParam("to_lon") double toLon,
            @Parameter(description = "Travel mode (e.g. DRIVING, CYCLING, WALKING)")
            @RequestParam(value = "mode", defaultValue = "DRIVING") String mode) {

        log.debug("GET /api/v1/geo/route from=({},{}) to=({},{}) mode={}", fromLat, fromLon, toLat, toLon, mode);
        RouteSegment segment = routingPort.getRoute(fromLat, fromLon, toLat, toLon, mode);
        return ResponseEntity.ok(segment);
    }

    // -------------------------------------------------------------------------
    // GET /poi  – points of interest
    // -------------------------------------------------------------------------

    @Operation(
        summary     = "Discover points of interest",
        description = "Returns POIs within the specified radius around the given coordinates, "
                    + "optionally filtered by OSM tag category."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "POI list"),
        @ApiResponse(responseCode = "502", description = "Upstream POI service unavailable",
                     content = @Content(schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class)))
    })
    @GetMapping(value = "/poi", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<PoiResult>> poi(
            @Parameter(description = "Centre latitude", required = true)
            @RequestParam("lat") double lat,
            @Parameter(description = "Centre longitude", required = true)
            @RequestParam("lon") double lon,
            @Parameter(description = "Search radius in metres (default 2000)")
            @RequestParam(value = "radius", defaultValue = "2000") int radius,
            @Parameter(description = "OSM tag category filter (e.g. tourism, restaurant, museum)")
            @RequestParam(value = "category", required = false) String category) {

        log.debug("GET /api/v1/geo/poi lat={} lon={} radius={} category={}", lat, lon, radius, category);
        List<PoiResult> results = poiDiscoveryPort.findPoi(lat, lon, radius, category);
        return ResponseEntity.ok(results);
    }
}
