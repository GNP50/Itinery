package com.travel.itinerary.impl.adapter.inbound.rest;

import com.travel.queue.dto.QueueStatusDTO;
import com.travel.itinerary.api.port.inbound.QueryQueueUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST adapter exposing queue administration endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/queue")
@RequiredArgsConstructor
@Tag(name = "Queue", description = "Processing queue administration and monitoring")
public class QueueRestController {

    private final QueryQueueUseCase queryQueueUseCase;

    // -------------------------------------------------------------------------
    // GET /status
    // -------------------------------------------------------------------------

    @Operation(
        summary     = "Get queue status",
        description = "Returns a snapshot of the itinerary processing queue including "
                    + "queue length, active processing count, max concurrency, and ordered item list."
    )
    @ApiResponse(responseCode = "200", description = "Queue status retrieved")
    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<QueueStatusDTO.Response> getStatus() {
        log.debug("GET /api/v1/queue/status");
        return ResponseEntity.ok(queryQueueUseCase.getQueueStatus());
    }
}
