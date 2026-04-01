package com.travel.itinerary.api.vo;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Immutable value object representing a single point of interest (POI)
 * returned by a POI discovery query.
 *
 * @param osmId     OpenStreetMap identifier of the feature; may be {@code null}
 *                  for non-OSM data sources
 * @param name      display name of the POI; may be {@code null}
 * @param category  high-level category tag (e.g. {@code "tourism"}, {@code "restaurant"})
 * @param lat       WGS-84 latitude of the POI centroid
 * @param lon       WGS-84 longitude of the POI centroid
 * @param tags      raw key/value OSM tags or provider-specific metadata;
 *                  never {@code null}, may be empty
 */
public record PoiResult(
        Long osmId,
        String name,
        String category,
        BigDecimal lat,
        BigDecimal lon,
        Map<String, String> tags
) {}
