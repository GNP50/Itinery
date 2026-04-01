package com.travel.itinerary.impl.domain.enums;

/**
 * Supported modes of transport for an itinerary.
 * <p>
 * Each constant maps to an OSRM routing profile via {@link #getOsrmProfile()}.
 */
public enum TravelMode {

    /** Private car or similar road vehicle. */
    CAR("driving"),

    /** Bicycle or similar human-powered two-wheel vehicle. */
    BIKE("cycling"),

    /** On foot. */
    WALK("foot"),

    /** Public transit (bus, metro, train). */
    TRANSIT("driving");   // OSRM does not natively support transit; use driving as fallback

    private final String osrmProfile;

    TravelMode(String osrmProfile) {
        this.osrmProfile = osrmProfile;
    }

    /**
     * Returns the OSRM routing-engine profile name corresponding to this travel mode.
     *
     * @return non-null, lower-case OSRM profile string (e.g. {@code "driving"}, {@code "cycling"})
     */
    public String getOsrmProfile() {
        return osrmProfile;
    }
}
