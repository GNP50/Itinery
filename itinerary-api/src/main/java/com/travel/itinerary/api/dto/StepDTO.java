package com.travel.itinerary.api.dto;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Container interface grouping all Step-related request/response DTOs.
 */
public interface StepDTO {

    /**
     * Payload supplied by the client when creating or updating a step.
     *
     * @param placeName   free-text name of the place (used for geocoding)
     * @param notes       optional traveller notes for this stop
     * @param arrivalDate ISO-8601 date or datetime string for the planned arrival
     * @param preferences optional step-level preferences (overrides trip-level preferences)
     */
    record Request(
            String placeName,
            String notes,
            String arrivalDate,
            Map<String, Object> preferences
    ) {}

    /**
     * Fully enriched step representation returned to the client.
     *
     * @param id                     step UUID
     * @param stepOrder              1-based position in the itinerary
     * @param placeName              canonical name resolved from geocoding
     * @param city                   city component of the resolved address
     * @param province               province / state component
     * @param region                 region component
     * @param country                full country name
     * @param countryCode            ISO 3166-1 alpha-2 country code
     * @param latitude               WGS-84 latitude of the stop
     * @param longitude              WGS-84 longitude of the stop
     * @param osmId                  OpenStreetMap node/way/relation identifier
     * @param notes                  traveller-supplied notes
     * @param aiDescription          AI-generated description of the place
     * @param aiTips                 AI-generated travel tips (may be serialised as a JSON array)
     * @param distanceFromPrevKm     road/path distance from the previous step in kilometres
     * @param durationFromPrevMin    estimated travel time from the previous step in minutes
     * @param routeGeometryFromPrev  GeoJSON geometry object (LineString) of the route from the previous step
     * @param poiNearby              list of nearby points of interest (serialised as a JSON array)
     * @param status                 processing status of this individual step
     * @param arrivalDate            ISO-8601 planned arrival date/datetime
     * @param preferences            step-level preferences (overrides trip-level preferences if present)
     */
    record Response(
            UUID id,
            int stepOrder,
            String placeName,
            String city,
            String province,
            String region,
            String country,
            String countryCode,
            BigDecimal latitude,
            BigDecimal longitude,
            Long osmId,
            String notes,
            String aiDescription,
            String aiTips,
            BigDecimal distanceFromPrevKm,
            Integer durationFromPrevMin,
            Object routeGeometryFromPrev,
            Object poiNearby,
            String status,
            String arrivalDate,
            Map<String, Object> preferences
    ) {}
}
