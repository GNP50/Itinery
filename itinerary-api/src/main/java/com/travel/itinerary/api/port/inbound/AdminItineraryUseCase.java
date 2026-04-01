package com.travel.itinerary.api.port.inbound;

import com.travel.itinerary.api.dto.AdminDTO;

/**
 * Use case port for admin operations on itineraries.
 * <p>
 * Provides read-only access to all itineraries across all users with pagination support.
 * All operations should be restricted to users with ADMIN role.
 */
public interface AdminItineraryUseCase {

    /**
     * List all itineraries with owner information (paginated).
     * <p>
     * Returns a paginated list of all itineraries in the system, including
     * owner details from the user table. This is an admin-only operation.
     *
     * @param page zero-based page number
     * @param size number of items per page
     * @return paginated response with itinerary summaries and owner information
     */
    AdminDTO.PagedItineraries listAllItineraries(int page, int size);
}

