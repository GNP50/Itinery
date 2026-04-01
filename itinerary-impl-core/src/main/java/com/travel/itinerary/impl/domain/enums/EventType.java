package com.travel.itinerary.impl.domain.enums;

/**
 * Discriminator values for the {@code ItineraryEvent} audit-log entity.
 * <p>
 * Each constant represents a distinct domain operation recorded in the
 * {@code itinerary_events} table.
 */
public enum EventType {

    /** A new itinerary was created and persisted. */
    CREATED,

    /** The itinerary was placed in the processing queue. */
    QUEUED,

    /** Asynchronous processing of the itinerary started. */
    PROCESSING_STARTED,

    /** A single step was geocoded, routed and/or AI-enriched. */
    STEP_PROCESSED,

    /** All steps were successfully processed; itinerary is now COMPLETED. */
    COMPLETED,

    /** Processing failed with an unrecoverable error. */
    FAILED,

    /** The itinerary's queue position was updated. */
    POSITION_UPDATED,

    /** The itinerary was cancelled by its owner. */
    CANCELLED
}
