package com.travel.geo.api.port.outbound;

import com.travel.itinerary.api.vo.GeoResult;
import com.travel.itinerary.api.vo.PoiResult;
import com.travel.itinerary.api.vo.RouteSegment;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Outbound port for accessing and modifying itinerary step data.
 * <p>
 * This port abstracts the itinerary persistence layer, allowing geo domain
 * workers to read step data and write back enriched results without depending
 * on the itinerary-impl module.
 * <p>
 * In a monolith deployment, this is implemented by delegating to
 * ItineraryPersistenceAdapter. In a microservice deployment, this could be
 * implemented as a gRPC or REST client calling the itinerary service API.
 */
public interface ItineraryStepDataPort {

    /**
     * Retrieves all steps for the given itinerary.
     *
     * @param itineraryId UUID of the itinerary
     * @return list of step data, ordered by stepOrder
     */
    List<StepData> findStepsByItineraryId(UUID itineraryId);

    /**
     * Retrieves the travel mode for the given itinerary.
     *
     * @param itineraryId UUID of the itinerary
     * @return travel mode as a string (e.g., "CAR", "BICYCLE"), or null if not set
     */
    String getTravelMode(UUID itineraryId);

    /**
     * Updates a single step with geocoding results.
     *
     * @param stepId step UUID
     * @param geoResult geocoding result
     */
    void updateStepWithGeocodingResult(UUID stepId, GeoResult geoResult);

    /**
     * Updates a single step with routing results from the previous step.
     *
     * @param stepId step UUID
     * @param routeSegment routing result
     */
    void updateStepWithRoutingResult(UUID stepId, RouteSegment routeSegment);

    /**
     * Updates a single step with POI discovery results.
     *
     * @param stepId step UUID
     * @param pois list of POI results
     */
    void updateStepWithPoiResults(UUID stepId, List<PoiResult> pois);

    /**
     * Updates total distance and duration on the itinerary.
     *
     * @param itineraryId UUID of the itinerary
     * @param totalDistanceKm total distance in kilometres
     * @param totalDurationMin total duration in minutes
     */
    void updateItineraryTotals(UUID itineraryId, BigDecimal totalDistanceKm, int totalDurationMin);

    /**
     * Marks the itinerary as COMPLETED, clears queue position, and publishes
     * the ItineraryCompletedEvent.
     *
     * @param itineraryId UUID of the itinerary
     */
    void markItineraryCompleted(UUID itineraryId);

    /**
     * Marks the itinerary status as PROCESSING.
     *
     * @param itineraryId UUID of the itinerary
     */
    void markItineraryProcessing(UUID itineraryId);

    /**
     * Immutable data carrier for itinerary step information needed by geo workers.
     *
     * @param id step UUID
     * @param stepOrder 1-based position in itinerary
     * @param placeName place name for geocoding
     * @param latitude WGS-84 latitude (may be null before geocoding)
     * @param longitude WGS-84 longitude (may be null before geocoding)
     * @param city city component
     * @param region region component
     * @param country country component
     */
    record StepData(
            UUID id,
            int stepOrder,
            String placeName,
            BigDecimal latitude,
            BigDecimal longitude,
            String city,
            String region,
            String country
    ) {}
}
