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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Outbound adapter that fulfils {@link GeocodingPort} by calling the
 * Nominatim OpenStreetMap geocoding REST API.
 *
 * <p>Nominatim's usage policy mandates <strong>at most 1 request per
 * second</strong>, which is enforced via a Resilience4j
 * {@link RateLimiter} named {@code "nominatim"}.  Results are cached
 * in {@link CachePort} for 7 days to reduce upstream traffic.
 */
@Slf4j
@Deprecated(since = "replaced by PhotonAdapter")
public class NominatimAdapter implements GeocodingPort {

    private static final String SERVICE_NAME  = "Nominatim";
    private static final Duration CACHE_TTL   = Duration.ofDays(7);
    private static final String CACHE_PREFIX  = "geo:nominatim:";

    private final String        baseUrl;
    private final String        userAgent;
    private final HttpClient    httpClient;
    private final ObjectMapper  objectMapper;
    private final CachePort     cachePort;
    private final RateLimiter   rateLimiter;

    public NominatimAdapter(
            @Value("${nominatim.base-url:https://nominatim.openstreetmap.org}") String baseUrl,
            @Value("${nominatim.user-agent:TravelItineraryPlatform/1.0 (contact@example.com)}") String userAgent,
            ObjectMapper objectMapper,
            CachePort cachePort,
            RateLimiterRegistry rateLimiterRegistry) {

        this.baseUrl     = baseUrl;
        this.userAgent   = userAgent;
        this.objectMapper = objectMapper;
        this.cachePort   = cachePort;
        this.rateLimiter = rateLimiterRegistry.rateLimiter("nominatim");
        this.httpClient  = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // -------------------------------------------------------------------------
    // GeocodingPort
    // -------------------------------------------------------------------------

    /**
     * Forward-geocode a free-text query, optionally restricted to a country.
     *
     * <p>Results are cached for {@value #CACHE_TTL} days.  The rate limiter
     * blocks until a permit is available (up to the configured timeout) before
     * issuing the HTTP request.
     *
     * @param query       free-text search string; must not be blank
     * @param countryCode ISO 3166-1 alpha-2 country code, or {@code null}
     * @return best-matching {@link GeoResult}; never {@code null}
     * @throws GeocodingException on upstream failure or no results found
     */
    @Override
    public GeoResult geocode(String query, String countryCode) {
        String cacheKey = buildCacheKey(query, countryCode);
        Optional<GeoResult> cached = cachePort.get(cacheKey);
        if (cached.isPresent()) {
            log.debug("Cache hit for geocode key={}", cacheKey);
            return cached.get();
        }

        return RateLimiter.decorateSupplier(rateLimiter, () -> {
            try {
                UriComponentsBuilder uriBuilder = UriComponentsBuilder
                        .fromUriString(baseUrl)
                        .path("/search")
                        .queryParam("format", "jsonv2")
                        .queryParam("addressdetails", "1")
                        .queryParam("accept-language", "it")
                        .queryParam("limit", "5")
                        .queryParam("q", query);

                if (countryCode != null && !countryCode.isBlank()) {
                    uriBuilder.queryParam("countrycodes", countryCode.toLowerCase());
                }

                String responseBody = executeGet(uriBuilder.build().toUri());
                JsonNode root = objectMapper.readTree(responseBody);

                if (!root.isArray() || root.isEmpty()) {
                    throw new GeocodingException(
                            "No results found for query='" + query + "' countryCode='" + countryCode + "'");
                }

                GeoResult result = mapToGeoResult(root.get(0));
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

    /**
     * Reverse-geocode WGS-84 coordinates to a structured address.
     *
     * @param lat WGS-84 latitude
     * @param lon WGS-84 longitude
     * @return resolved {@link GeoResult}; never {@code null}
     * @throws GeocodingException on upstream failure or no result
     */
    @Override
    public GeoResult reverseGeocode(double lat, double lon) {
        String cacheKey = CACHE_PREFIX + "reverse:" + lat + ":" + lon;
        Optional<GeoResult> cached = cachePort.get(cacheKey);
        if (cached.isPresent()) {
            log.debug("Cache hit for reverseGeocode key={}", cacheKey);
            return cached.get();
        }

        return RateLimiter.decorateSupplier(rateLimiter, () -> {
            try {
                URI uri = UriComponentsBuilder
                        .fromUriString(baseUrl)
                        .path("/reverse")
                        .queryParam("format", "jsonv2")
                        .queryParam("addressdetails", "1")
                        .queryParam("lat", lat)
                        .queryParam("lon", lon)
                        .build()
                        .toUri();

                String responseBody = executeGet(uri);
                JsonNode root = objectMapper.readTree(responseBody);

                if (root.has("error")) {
                    throw new GeocodingException(
                            "Reverse geocoding failed: " + root.path("error").asText());
                }

                GeoResult result = mapToGeoResult(root);
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

    /**
     * Execute an HTTP GET against the given URI, adding the required
     * {@code User-Agent} header.
     *
     * @param uri target URI
     * @return response body string
     * @throws ExternalServiceException on non-2xx response or I/O error
     */
    private String executeGet(URI uri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            throw new ExternalServiceException(SERVICE_NAME,
                    "Rate-limited by Nominatim (HTTP 429)");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ExternalServiceException(SERVICE_NAME,
                    "HTTP " + response.statusCode() + " from Nominatim: " + response.body());
        }

        return response.body();
    }

    /**
     * Map a single Nominatim JSON result node to a {@link GeoResult}.
     *
     * @param node a Nominatim result JSON object (from {@code /search} or {@code /reverse})
     * @return populated {@link GeoResult}
     */
    private GeoResult mapToGeoResult(JsonNode node) {
        JsonNode address = node.path("address");

        String displayName  = node.path("display_name").asText(null);
        BigDecimal lat      = new BigDecimal(node.path("lat").asText("0"));
        BigDecimal lon      = new BigDecimal(node.path("lon").asText("0"));
        long osmId          = node.path("osm_id").asLong(0L);

        // City can appear under multiple keys in the address block
        String city = firstNonNull(
                address.path("city").asText(null),
                address.path("town").asText(null),
                address.path("village").asText(null),
                address.path("municipality").asText(null));

        String province     = address.path("county").asText(null);
        String region       = firstNonNull(
                address.path("state").asText(null),
                address.path("region").asText(null));
        String country      = address.path("country").asText(null);
        String countryCode  = address.path("country_code").asText(null);
        if (countryCode != null) {
            countryCode = countryCode.toUpperCase();
        }

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

    /** Returns the first non-null, non-empty value from the varargs, or {@code null}. */
    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T v : values) {
            if (v != null && !v.toString().isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String buildCacheKey(String query, String countryCode) {
        return CACHE_PREFIX + sanitize(query) + ":" + (countryCode != null ? countryCode.toLowerCase() : "any");
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("\\s+", "_");
    }
}
