package com.travel.itinerary.api.port.inbound;

import com.travel.itinerary.api.dto.ItineraryDTO;

/**
 * Use case port for listing itineraries owned by the current authenticated user.
 * <p>
 * Returns a lightweight summary of all itineraries (QUEUED, PROCESSING, COMPLETED, FAILED)
 * filtered by the user's UUID extracted from the JWT token.
 */
public interface ListItinerariesUseCase {

    /**
     * List all itineraries owned by the current authenticated user.
     *
     * @return response containing the list of itinerary summaries
     */
    ItineraryDTO.ListResponse listMyItineraries();
}
