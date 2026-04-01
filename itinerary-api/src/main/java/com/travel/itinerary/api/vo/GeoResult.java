package com.travel.itinerary.api.vo;

import java.math.BigDecimal;

/**
 * Immutable value object carrying the structured result of a geocoding
 * (forward or reverse) lookup.
 *
 * @param displayName  human-readable full address string returned by the provider
 * @param lat          WGS-84 latitude of the resolved location
 * @param lon          WGS-84 longitude of the resolved location
 * @param osmId        OpenStreetMap node/way/relation identifier; may be
 *                     {@code null} for non-OSM providers
 * @param city         city component of the resolved address; may be {@code null}
 * @param province     province or county component; may be {@code null}
 * @param region       region or state component; may be {@code null}
 * @param country      full country name; may be {@code null}
 * @param countryCode  ISO 3166-1 alpha-2 country code; may be {@code null}
 */
public record GeoResult(
        String displayName,
        BigDecimal lat,
        BigDecimal lon,
        Long osmId,
        String city,
        String province,
        String region,
        String country,
        String countryCode
) {}
