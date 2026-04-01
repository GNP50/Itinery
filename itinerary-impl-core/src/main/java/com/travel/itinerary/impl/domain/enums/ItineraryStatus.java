package com.travel.itinerary.impl.domain.enums;

/**
 * Lifecycle states of an {@code Itinerary} aggregate.
 *
 * <ul>
 *   <li>{@link #QUEUED}      – accepted and waiting in the processing queue</li>
 *   <li>{@link #PROCESSING}  – currently being geocoded / routed / AI-enriched</li>
 *   <li>{@link #COMPLETED}   – all steps successfully enriched</li>
 *   <li>{@link #FAILED}      – processing terminated with an unrecoverable error</li>
 *   <li>{@link #CANCELLED}   – cancelled by the owner before processing finished</li>
 * </ul>
 */
public enum ItineraryStatus {

    /** Itinerary has been accepted and placed in the processing queue. */
    QUEUED,

    /** Itinerary is actively being processed (geocoding, routing, AI enrichment). */
    PROCESSING,

    /** All processing steps completed successfully. */
    COMPLETED,

    /** Processing terminated with an unrecoverable error. */
    FAILED,

    /** Owner cancelled the itinerary before or during processing. */
    CANCELLED
}
