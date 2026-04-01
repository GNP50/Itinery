package com.travel.itinerary.api.port.inbound;


import com.travel.queue.dto.QueueStatusDTO;

/**
 * Inbound port: administrative query for the current state of the itinerary
 * processing queue.
 */
public interface QueryQueueUseCase {

    /**
     * Return a snapshot of the processing queue, including all waiting items
     * and concurrency configuration.
     *
     * @return {@link QueueStatusDTO.Response} with queue statistics and item list
     */
    QueueStatusDTO.Response getQueueStatus();
}
