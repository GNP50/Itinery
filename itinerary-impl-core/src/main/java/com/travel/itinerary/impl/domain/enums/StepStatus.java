package com.travel.itinerary.impl.domain.enums;

/**
 * Lifecycle states of an individual {@code ItineraryStep}.
 *
 * <ul>
 *   <li>{@link #PENDING}  – step has not yet been reached by the traveller</li>
 *   <li>{@link #CURRENT}  – traveller is currently at this step</li>
 *   <li>{@link #VISITED}  – traveller has completed this step</li>
 *   <li>{@link #SKIPPED}  – traveller explicitly skipped this step</li>
 * </ul>
 */
public enum StepStatus {

    /** Step is upcoming; not yet reached by the traveller. */
    PENDING,

    /** Traveller is currently at this step. */
    CURRENT,

    /** Traveller has completed and left this step. */
    VISITED,

    /** Traveller explicitly skipped this step. */
    SKIPPED
}
