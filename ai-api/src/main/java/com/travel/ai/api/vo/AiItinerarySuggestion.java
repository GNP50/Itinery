package com.travel.ai.api.vo;

import java.util.List;

/**
 * Immutable value object carrying AI-generated high-level suggestions for an
 * entire itinerary.
 *
 * @param summary          one-paragraph narrative overview of the itinerary,
 *                         covering the main themes and highlights
 * @param highlights       ordered list of the most notable experiences or places
 *                         across the full trip
 * @param estimatedBudget  rough budget estimate for the itinerary
 *                         (e.g. {@code "€200–€350 per person"}); may be {@code null}
 * @param generalTips      general tips applicable to the whole trip (e.g. best
 *                         season to travel, visa advice, packing suggestions)
 */
public record AiItinerarySuggestion(
        String summary,
        List<String> highlights,
        String estimatedBudget,
        List<String> generalTips
) {}
