package com.travel.geo.impl.adapter.outbound.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.itinerary.api.port.outbound.CachePort;
import com.travel.itinerary.api.port.outbound.PoiDiscoveryPort;
import com.travel.itinerary.api.vo.PoiResult;
import com.travel.geo.impl.exception.ExternalServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Outbound adapter that fulfils {@link PoiDiscoveryPort} by querying the
 * Overpass API (OpenStreetMap data).
 *
 * <p>A Resilience4j {@link CircuitBreaker} named {@code "overpass"} protects
 * against upstream unavailability.  When the circuit is open the fallback
 * returns an empty list so the application can continue processing without POI
 * enrichment.
 *
 * <p>Results are cached in {@link CachePort} for 24 hours.
 */
@Slf4j
@Component
public class OverpassAdapter implements PoiDiscoveryPort {

    private static final String   SERVICE_NAME = "Overpass";
    private static final Duration CACHE_TTL    = Duration.ofHours(24);
    private static final String   CACHE_PREFIX = "geo:overpass:";

    private final String       baseUrl;
    private final HttpClient   httpClient;
    private final ObjectMapper objectMapper;
    private final CachePort    cachePort;

    public OverpassAdapter(
            @Value("${overpass.base-url:https://overpass-api.de/api/interpreter}") String baseUrl,
            ObjectMapper objectMapper,
            CachePort cachePort) {

        this.baseUrl      = baseUrl;
        this.objectMapper = objectMapper;
        this.cachePort    = cachePort;
        this.httpClient   = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    // -------------------------------------------------------------------------
    // PoiDiscoveryPort
    // -------------------------------------------------------------------------

    /**
     * Find points of interest within a radius around a coordinate using
     * Overpass QL queries against the OpenStreetMap dataset.
     *
     * <p>The method is wrapped in a {@code @CircuitBreaker(name = "overpass")}
     * with a fallback that returns an empty list.
     *
     * @param lat          WGS-84 latitude of the search centre
     * @param lon          WGS-84 longitude of the search centre
     * @param radiusMeters search radius in metres; must be positive
     * @param category     OSM tag category filter (e.g. {@code "tourism"},
     *                     {@code "restaurant"}, {@code "museum"}); or
     *                     {@code null} to return all categories
     * @return unmodifiable list of {@link PoiResult}s; never {@code null}
     */
    @Override
    @CircuitBreaker(name = "overpass", fallbackMethod = "findPoiFallback")
    public List<PoiResult> findPoi(double lat, double lon,
                                   int radiusMeters, String category) {

        String cacheKey = buildCacheKey(lat, lon, radiusMeters, category);
        Optional<List<PoiResult>> cached = cachePort.get(cacheKey);
        if (cached.isPresent()) {
            log.debug("Cache hit for Overpass POI key={}", cacheKey);
            return cached.get();
        }

        try {
            String overpassQuery = buildOverpassQuery(lat, lon, radiusMeters, category);
            String formBody      = "data=" + URLEncoder.encode(overpassQuery, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl))
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ExternalServiceException(SERVICE_NAME,
                        "HTTP " + response.statusCode() + " from Overpass");
            }

            JsonNode root  = objectMapper.readTree(response.body());
            JsonNode nodes = root.path("elements");

            List<PoiResult> results = new ArrayList<>();
            for (JsonNode node : nodes) {
                PoiResult poi = mapToPoi(node, category);
                if (poi != null) {
                    results.add(poi);
                }
            }

            List<PoiResult> immutable = Collections.unmodifiableList(results);
            cachePort.put(cacheKey, immutable, CACHE_TTL);
            log.debug("Overpass returned {} POIs for lat={} lon={} radius={} category={}",
                    results.size(), lat, lon, radiusMeters, category);
            return immutable;

        } catch (PoiDiscoveryException | ExternalServiceException de) {
            throw de;
        } catch (Exception ex) {
            throw new ExternalServiceException(SERVICE_NAME,
                    "POI discovery failed for lat=" + lat + " lon=" + lon, ex);
        }
    }

    // -------------------------------------------------------------------------
    // Fallback
    // -------------------------------------------------------------------------

    /**
     * Fallback invoked when the Overpass circuit is open.
     * Returns an empty list so the caller can proceed without POI data.
     *
     * @param lat          latitude
     * @param lon          longitude
     * @param radiusMeters radius
     * @param category     category filter
     * @param cause        triggering exception
     * @return empty, unmodifiable list
     */
    @SuppressWarnings("unused") // invoked by Resilience4j via reflection
    public List<PoiResult> findPoiFallback(double lat, double lon,
                                            int radiusMeters, String category,
                                            Throwable cause) {
        log.warn("Overpass circuit open or error ({}), returning empty POI list", cause.getMessage());
        return Collections.emptyList();
    }

    // -------------------------------------------------------------------------
    // Overpass QL builder
    // -------------------------------------------------------------------------

    /**
     * Build an Overpass QL query targeting {@code node} elements within the
     * requested radius, filtered to the relevant OSM tag keys for the given
     * category.
     *
     * @param lat          centre latitude
     * @param lon          centre longitude
     * @param radiusMeters search radius
     * @param category     optional category filter
     * @return Overpass QL query string
     */
    private static String buildOverpassQuery(double lat, double lon,
                                              int radiusMeters, String category) {

        String around = "(around:" + radiusMeters + "," + lat + "," + lon + ")";
        StringBuilder sb = new StringBuilder("[out:json][timeout:10];(");

        if (category == null || category.isBlank()) {
            // Broad query: tourism + amenity + historic
            sb.append("node[tourism]").append(around).append(";");
            sb.append("node[amenity]").append(around).append(";");
            sb.append("node[historic]").append(around).append(";");
        } else {
            String cat = category.toLowerCase();
            switch (cat) {
                case "tourism":
                    sb.append("node[tourism]").append(around).append(";");
                    break;
                case "restaurant", "cafe", "bar", "fast_food", "pub":
                    sb.append("node[amenity=").append(cat).append("]").append(around).append(";");
                    break;
                case "museum", "gallery":
                    sb.append("node[tourism=museum]").append(around).append(";");
                    sb.append("node[tourism=gallery]").append(around).append(";");
                    break;
                case "historic":
                    sb.append("node[historic]").append(around).append(";");
                    break;
                case "amenity":
                    sb.append("node[amenity]").append(around).append(";");
                    break;
                default:
                    // Attempt both tourism and amenity with the given value
                    sb.append("node[tourism=").append(cat).append("]").append(around).append(";");
                    sb.append("node[amenity=").append(cat).append("]").append(around).append(";");
                    sb.append("node[historic=").append(cat).append("]").append(around).append(";");
            }
        }

        sb.append(");out body;");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    /**
     * Map a single Overpass {@code element} JSON node to a {@link PoiResult}.
     *
     * @param node     an Overpass JSON element object
     * @param category the category filter that was used in the query
     * @return {@link PoiResult} or {@code null} if the node lacks coordinates
     */
    private static PoiResult mapToPoi(JsonNode node, String category) {
        if (!node.has("lat") || !node.has("lon")) {
            return null;  // way/relation without centre point — skip
        }

        long       osmId       = node.path("id").asLong(0L);
        BigDecimal lat         = new BigDecimal(node.path("lat").asText("0"));
        BigDecimal lon         = new BigDecimal(node.path("lon").asText("0"));
        JsonNode   tagsNode    = node.path("tags");

        String name = tagsNode.path("name").asText(null);

        // Determine effective category from tags
        String effectiveCategory = resolveCategory(tagsNode, category);

        // Collect all tags as a flat Map<String, String>
        Map<String, String> tags = new HashMap<>();
        if (tagsNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = tagsNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                tags.put(entry.getKey(), entry.getValue().asText());
            }
        }

        return new PoiResult(
                osmId == 0L ? null : osmId,
                name,
                effectiveCategory,
                lat,
                lon,
                Collections.unmodifiableMap(tags));
    }

    /**
     * Resolve the most specific category from the element's tags.
     *
     * @param tags          the OSM tags node
     * @param requestedCat  the category requested by the caller
     * @return resolved category string
     */
    private static String resolveCategory(JsonNode tags, String requestedCat) {
        if (requestedCat != null && !requestedCat.isBlank()) {
            return requestedCat;
        }
        String tourism  = tags.path("tourism").asText(null);
        String amenity  = tags.path("amenity").asText(null);
        String historic = tags.path("historic").asText(null);
        if (tourism  != null && !tourism.isBlank())  return "tourism";
        if (amenity  != null && !amenity.isBlank())  return "amenity";
        if (historic != null && !historic.isBlank()) return "historic";
        return "unknown";
    }

    private static String buildCacheKey(double lat, double lon,
                                        int radiusMeters, String category) {
        return CACHE_PREFIX + lat + ":" + lon + ":"
                + radiusMeters + ":"
                + (category != null ? category.toLowerCase() : "all");
    }
}
