package com.travel.geo.impl.adapter.outbound.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.itinerary.api.port.outbound.CachePort;
import com.travel.itinerary.api.port.outbound.GeocodingPort;
import com.travel.itinerary.api.vo.GeoResult;
import com.travel.geo.impl.exception.ExternalServiceException;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Outbound adapter that fulfils {@link GeocodingPort} via the
 * Photon geocoding API (https://photon.komoot.io).
 *
 * <p>Photon is an open-source geocoder powered by OpenStreetMap data.
 * It returns GeoJSON FeatureCollections — coordinates are {@code [lon, lat]}
 * in {@code geometry.coordinates}.
 *
 * <p>Results are cached for 7 days to reduce upstream traffic.
 * A Resilience4j {@link RateLimiter} named {@code "photon"} is used as
 * a courtesy throttle; Photon has no strict per-second limit but enforces
 * fair-use.
 */
@Slf4j
@Component
public class PhotonAdapter implements GeocodingPort {

    private static final String   SERVICE_NAME = "Photon";
    private static final Duration CACHE_TTL    = Duration.ofDays(7);
    private static final String   CACHE_PREFIX = "geo:photon:";

    private final String       baseUrl;
    private final HttpClient   httpClient;
    private final ObjectMapper objectMapper;
    private final CachePort    cachePort;
    private final RateLimiter  rateLimiter;

    public PhotonAdapter(
            @Value("${photon.base-url:https://photon.komoot.io}") String baseUrl,
            ObjectMapper objectMapper,
            CachePort cachePort,
            RateLimiterRegistry rateLimiterRegistry) {

        this.baseUrl      = baseUrl;
        this.objectMapper = objectMapper;
        this.cachePort    = cachePort;
        this.rateLimiter  = rateLimiterRegistry.rateLimiter("photon");
        this.httpClient   = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // -------------------------------------------------------------------------
    // GeocodingPort
    // -------------------------------------------------------------------------

    @Override
    public GeoResult geocode(String query, String countryCode) {
        String cacheKey = buildCacheKey(query, countryCode);
        // Use typed get() to avoid ClassCastException when Redis deserialises to LinkedHashMap
        Optional<GeoResult> cached = cachePort.get(cacheKey, GeoResult.class);
        if (cached.isPresent()) {
            log.debug("Cache hit for geocode key={}", cacheKey);
            return cached.get();
        }

        return RateLimiter.decorateSupplier(rateLimiter, () -> {
            try {
                URI uri = UriComponentsBuilder
                        .fromUriString(baseUrl)
                        .path("/api")
                        .queryParam("q", query)
                        .queryParam("limit", "5")
                        .queryParam("lang", "en")
                        .build()
                        .toUri();

                String responseBody = executeGet(uri);
                JsonNode features = objectMapper.readTree(responseBody).path("features");

                if (!features.isArray() || features.isEmpty()) {
                    throw new GeocodingException(
                            "No results found for query='" + query + "'");
                }

                // Prefer the first feature whose countrycode matches the filter
                JsonNode best = features.get(0);
                if (countryCode != null && !countryCode.isBlank()) {
                    for (JsonNode f : features) {
                        if (countryCode.equalsIgnoreCase(
                                f.path("properties").path("countrycode").asText(""))) {
                            best = f;
                            break;
                        }
                    }
                }

                GeoResult result = mapToGeoResult(best);
                cachePort.put(cacheKey, result, CACHE_TTL);
                log.debug("Geocoded query='{}' -> lat={} lon={}", query, result.lat(), result.lon());
                return result;

            } catch (GeocodingException ge) {
                throw ge;
            } catch (Exception ex) {
                throw new ExternalServiceException(
                        SERVICE_NAME, "Geocoding failed for query='" + query + "'", ex);
            }
        }).get();
    }

    @Override
    public List<GeoResult> searchPlaces(String query, String countryCode, int limit) {
        String cacheKey = buildCacheKey(query, countryCode) + ":list:" + limit;
        // Use typed get() to avoid ClassCastException
        Optional<GeoResult[]> cached = cachePort.get(cacheKey, GeoResult[].class);
        if (cached.isPresent()) {
            log.debug("Cache hit for searchPlaces key={}", cacheKey);
            return List.of(cached.get());
        }

        return RateLimiter.decorateSupplier(rateLimiter, () -> {
            try {
                URI uri = UriComponentsBuilder
                        .fromUriString(baseUrl)
                        .path("/api")
                        .queryParam("q", query)
                        .queryParam("limit", Math.min(limit, 10))
                        .queryParam("lang", "en")
                        .build()
                        .toUri();

                String responseBody = executeGet(uri);
                JsonNode features = objectMapper.readTree(responseBody).path("features");

                if (!features.isArray() || features.isEmpty()) {
                    return List.<GeoResult>of();
                }

                List<GeoResult> results = new ArrayList<>();
                for (JsonNode feature : features) {
                    if (countryCode != null && !countryCode.isBlank()) {
                        String featureCountry = feature.path("properties").path("countrycode").asText("");
                        if (!countryCode.equalsIgnoreCase(featureCountry)) continue;
                    }
                    results.add(mapToGeoResult(feature));
                    if (results.size() >= limit) break;
                }

                // Fallback: if country filter discarded everything, return unfiltered top results
                if (results.isEmpty()) {
                    for (JsonNode feature : features) {
                        results.add(mapToGeoResult(feature));
                        if (results.size() >= limit) break;
                    }
                }

                cachePort.put(cacheKey, results.toArray(new GeoResult[0]), CACHE_TTL);
                log.debug("searchPlaces query='{}' -> {} results", query, results.size());
                return results;

            } catch (Exception ex) {
                throw new ExternalServiceException(
                        SERVICE_NAME, "searchPlaces failed for query='" + query + "'", ex);
            }
        }).get();
    }

    @Override
    public GeoResult reverseGeocode(double lat, double lon) {
        String cacheKey = CACHE_PREFIX + "reverse:" + lat + ":" + lon;
        Optional<GeoResult> cached = cachePort.get(cacheKey, GeoResult.class);
        if (cached.isPresent()) {
            log.debug("Cache hit for reverseGeocode key={}", cacheKey);
            return cached.get();
        }

        return RateLimiter.decorateSupplier(rateLimiter, () -> {
            try {
                URI uri = UriComponentsBuilder
                        .fromUriString(baseUrl)
                        .path("/reverse")
                        .queryParam("lat", lat)
                        .queryParam("lon", lon)
                        .build()
                        .toUri();

                String responseBody = executeGet(uri);
                JsonNode features = objectMapper.readTree(responseBody).path("features");

                if (!features.isArray() || features.isEmpty()) {
                    throw new GeocodingException(
                            "No results found for lat=" + lat + " lon=" + lon);
                }

                GeoResult result = mapToGeoResult(features.get(0));
                cachePort.put(cacheKey, result, CACHE_TTL);
                log.debug("Reverse geocoded lat={} lon={} -> '{}'", lat, lon, result.displayName());
                return result;

            } catch (GeocodingException ge) {
                throw ge;
            } catch (Exception ex) {
                throw new ExternalServiceException(
                        SERVICE_NAME, "Reverse geocoding failed for lat=" + lat + " lon=" + lon, ex);
            }
        }).get();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String executeGet(URI uri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ExternalServiceException(SERVICE_NAME,
                    "HTTP " + response.statusCode() + " from Photon: " + response.body());
        }
        return response.body();
    }

    /**
     * Photon returns GeoJSON. Coordinates are {@code [lon, lat]} in
     * {@code geometry.coordinates}. Properties include: name, city, county,
     * state, country, countrycode, osm_id.
     */
    private GeoResult mapToGeoResult(JsonNode feature) {
        JsonNode coords     = feature.path("geometry").path("coordinates");
        JsonNode props      = feature.path("properties");

        BigDecimal lon = new BigDecimal(coords.get(0).asText("0"));
        BigDecimal lat = new BigDecimal(coords.get(1).asText("0"));

        String name        = props.path("name").asText(null);
        String city        = firstNonNull(props.path("city").asText(null), name);
        String province    = props.path("county").asText(null);
        String region      = firstNonNull(
                props.path("state").asText(null),
                props.path("region").asText(null));
        String country     = props.path("country").asText(null);
        String countryCode = props.path("countrycode").asText(null);
        if (countryCode != null) countryCode = countryCode.toUpperCase();
        long osmId = props.path("osm_id").asLong(0L);

        String displayName = buildDisplayName(name, city, province, region, country);

        return new GeoResult(
                displayName,
                lat,
                lon,
                osmId == 0L ? null : osmId,
                city,
                province,
                region,
                country,
                countryCode);
    }

    private static String buildDisplayName(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                if (!sb.isEmpty()) sb.append(", ");
                // Avoid repeating the same token (e.g. "Rome, Rome, Lazio, Italy")
                if (!sb.toString().contains(part)) sb.append(part);
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T v : values) {
            if (v != null && !v.toString().isBlank()) return v;
        }
        return null;
    }

    private static String buildCacheKey(String query, String countryCode) {
        return CACHE_PREFIX + sanitize(query) + ":"
                + (countryCode != null ? countryCode.toLowerCase() : "any");
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("\\s+", "_");
    }
}
