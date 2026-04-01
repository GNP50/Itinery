package com.travel.geo.impl.adapter.outbound.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.itinerary.api.port.outbound.CachePort;
import com.travel.itinerary.api.port.outbound.RoutingPort;
import com.travel.itinerary.api.vo.RouteSegment;
import com.travel.geo.impl.exception.ExternalServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Outbound adapter that fulfils {@link RoutingPort} by calling the OSRM
 * (Open Source Routing Machine) routing REST API.
 *
 * <p>A Resilience4j {@link CircuitBreaker} named {@code "osrm"} wraps every
 * routing call.  When the circuit is open, the fallback method
 * {@link #getRouteFallback} computes an approximate straight-line (Euclidean)
 * distance so the application can continue functioning degraded.
 *
 * <p>Results are cached in {@link CachePort} for 24 hours.
 */
@Slf4j
@Component
public class OsrmAdapter implements RoutingPort {

    private static final String   SERVICE_NAME    = "OSRM";
    private static final Duration CACHE_TTL       = Duration.ofHours(24);
    private static final String   CACHE_PREFIX    = "geo:osrm:";

    /** Earth radius in kilometres, used for the Euclidean fallback. */
    private static final double EARTH_RADIUS_KM = 6_371.0;

    private final String       baseUrl;
    private final HttpClient   httpClient;
    private final ObjectMapper objectMapper;
    private final CachePort    cachePort;

    public OsrmAdapter(
            @Value("${osrm.base-url:https://router.project-osrm.org}") String baseUrl,
            ObjectMapper objectMapper,
            CachePort cachePort) {

        this.baseUrl      = baseUrl;
        this.objectMapper = objectMapper;
        this.cachePort    = cachePort;
        this.httpClient   = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // -------------------------------------------------------------------------
    // RoutingPort
    // -------------------------------------------------------------------------

    /**
     * Calculate the optimal route between two WGS-84 coordinates.
     *
     * <p>The method is decorated with {@code @CircuitBreaker(name = "osrm")}
     * and {@code @Retry(name = "osrm")}.  When the circuit opens or retries are
     * exhausted the fallback {@link #getRouteFallback} is invoked automatically.
     *
     * @param fromLat    origin latitude
     * @param fromLon    origin longitude
     * @param toLat      destination latitude
     * @param toLon      destination longitude
     * @param travelMode routing profile: {@code "DRIVING"}, {@code "CYCLING"},
     *                   or {@code "WALKING"}
     * @return {@link RouteSegment} with distance, duration and GeoJSON geometry
     * @throws RoutingException if routing fails and the fallback is also unavailable
     */
    @Override
    @CircuitBreaker(name = "osrm", fallbackMethod = "getRouteFallback")
    @Retry(name = "osrm")
    public RouteSegment getRoute(double fromLat, double fromLon,
                                 double toLat,   double toLon,
                                 String travelMode) {

        String cacheKey = buildCacheKey(fromLat, fromLon, toLat, toLon, travelMode);
        Optional<RouteSegment> cached = cachePort.get(cacheKey);
        if (cached.isPresent()) {
            log.debug("Cache hit for OSRM route key={}", cacheKey);
            return cached.get();
        }

        String profile = resolveProfile(travelMode);

        // OSRM coordinate order: longitude,latitude
        String coordinates = fromLon + "," + fromLat + ";" + toLon + "," + toLat;
        URI uri = UriComponentsBuilder
                .fromUriString(baseUrl)
                .path("/route/v1/{profile}/{coordinates}")
                .queryParam("geometries", "geojson")
                .queryParam("overview",   "full")
                .queryParam("steps",      "false")
                .buildAndExpand(profile, coordinates)
                .toUri();

        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .GET()
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ExternalServiceException(SERVICE_NAME,
                        "HTTP " + response.statusCode() + " from OSRM");
            }

            JsonNode root = objectMapper.readTree(response.body());
            String code = root.path("code").asText("");
            if (!"Ok".equals(code)) {
                throw new RoutingException(
                        "OSRM returned code='" + code + "' for route request");
            }

            JsonNode route = root.path("routes").get(0);
            if (route == null || route.isMissingNode()) {
                throw new RoutingException("No routes returned by OSRM");
            }

            double distanceMeters = route.path("distance").asDouble(0);
            double durationSecs   = route.path("duration").asDouble(0);

            BigDecimal distanceKm  = BigDecimal.valueOf(distanceMeters / 1_000.0)
                    .setScale(3, RoundingMode.HALF_UP);
            int durationMin        = (int) Math.ceil(durationSecs / 60.0);

            String geoJson = null;
            JsonNode geometry = route.path("geometry");
            if (!geometry.isMissingNode()) {
                geoJson = objectMapper.writeValueAsString(geometry);
            }

            RouteSegment segment = new RouteSegment(distanceKm, durationMin, geoJson);
            cachePort.put(cacheKey, segment, CACHE_TTL);
            log.debug("OSRM route from ({},{}) to ({},{}) via {}: {}km {}min",
                    fromLat, fromLon, toLat, toLon, profile,
                    distanceKm, durationMin);
            return segment;

        } catch (RoutingException | ExternalServiceException re) {
            throw re;
        } catch (Exception ex) {
            throw new ExternalServiceException(SERVICE_NAME,
                    "Routing request failed", ex);
        }
    }

    // -------------------------------------------------------------------------
    // Fallback
    // -------------------------------------------------------------------------

    /**
     * Fallback invoked when the OSRM circuit is open or retries are exhausted.
     * Computes an approximate Haversine (great-circle) distance and estimates
     * duration based on average speed for the requested travel mode.
     *
     * @param fromLat    origin latitude
     * @param fromLon    origin longitude
     * @param toLat      destination latitude
     * @param toLon      destination longitude
     * @param travelMode travel mode string
     * @param cause      the exception that triggered the fallback
     * @return degraded {@link RouteSegment} with estimated values and no geometry
     */
    @SuppressWarnings("unused") // invoked by Resilience4j via reflection
    public RouteSegment getRouteFallback(double fromLat, double fromLon,
                                         double toLat,   double toLon,
                                         String travelMode, Throwable cause) {

        log.warn("OSRM circuit open or retry exhausted ({}), using Euclidean fallback", cause.getMessage());

        double distanceKm  = haversineKm(fromLat, fromLon, toLat, toLon);
        // Apply a road-distance correction factor
        double corrected   = distanceKm * 1.3;
        double speedKmh    = averageSpeedKmh(travelMode);
        int    durationMin = (int) Math.ceil((corrected / speedKmh) * 60.0);

        return new RouteSegment(
                BigDecimal.valueOf(corrected).setScale(3, RoundingMode.HALF_UP),
                durationMin,
                null  // no geometry available in fallback
        );
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Map a {@code travelMode} string to the corresponding OSRM profile name.
     *
     * @param travelMode one of {@code "DRIVING"}, {@code "CYCLING"}, {@code "WALKING"};
     *                   defaults to {@code "driving"} for unrecognised values
     * @return OSRM profile string
     */
    private static String resolveProfile(String travelMode) {
        if (travelMode == null) {
            return "driving";
        }
        return switch (travelMode.toUpperCase()) {
            case "CYCLING" -> "cycling";
            case "WALKING", "FOOT" -> "foot";
            default -> "driving";
        };
    }

    /**
     * Return a reasonable average speed in km/h for the given travel mode.
     *
     * @param travelMode travel mode string
     * @return average speed in km/h
     */
    private static double averageSpeedKmh(String travelMode) {
        if (travelMode == null) return 60.0;
        return switch (travelMode.toUpperCase()) {
            case "CYCLING"       -> 15.0;
            case "WALKING", "FOOT" -> 5.0;
            default              -> 60.0;  // DRIVING
        };
    }

    /**
     * Compute the Haversine (great-circle) distance between two WGS-84
     * coordinates in kilometres.
     */
    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    private static String buildCacheKey(double fromLat, double fromLon,
                                        double toLat,   double toLon,
                                        String mode) {
        return CACHE_PREFIX + fromLat + ":" + fromLon + ":"
                + toLat + ":" + toLon + ":"
                + (mode != null ? mode.toLowerCase() : "driving");
    }
}
