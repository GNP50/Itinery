package com.travel.ai.api.vo;

import java.util.List;

/**
 * Immutable value object carrying AI-generated enrichment data for a single
 * itinerary step.
 *
 * @param description          narrative description of the place tailored to the
 *                             traveller's interests and travel mode
 * @param tips                 actionable travel tips (e.g. best times to visit,
 *                             transport hints, accessibility notes)
 * @param mustSee              curated list of must-see highlights at or near
 *                             the stop
 * @param localFood            AI recommendation for local cuisine or restaurants
 *                             near the stop; may be {@code null}
 * @param recommendedDuration  suggested time to spend at this stop
 *                             (e.g. {@code "2-3 hours"}); may be {@code null}
 */
public record AiStepEnrichment(
        String description,
        List<String> tips,
        List<String> mustSee,
        String localFood,
        String recommendedDuration
) {}
