package com.travel.itinerary.impl.adapter.inbound.rest;

import com.travel.itinerary.api.dto.ItineraryDTO;
import com.travel.itinerary.api.dto.PositionUpdateDTO;
import com.travel.itinerary.api.port.inbound.*;
import com.travel.saga.dto.SagaInstanceDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST adapter exposing the itinerary use cases over HTTP.
 * <p>
 * All write endpoints require an {@code access_token} query parameter that is
 * issued at creation time.  The 202 status on POST signals that the request has
 * been accepted and queued for asynchronous enrichment.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/itineraries")
@RequiredArgsConstructor
@Tag(name = "Itineraries", description = "Create, query and manage travel itineraries")
public class ItineraryRestController {

    private final CreateItineraryUseCase  createUseCase;
    private final GetItineraryUseCase     getUseCase;
    private final UpdateItineraryUseCase  updateUseCase;
    private final DeleteItineraryUseCase  deleteUseCase;
    private final UpdatePositionUseCase   positionUseCase;
    private final ListItinerariesUseCase  listUseCase;
    private final ItinerarySagaQueryUseCase sagaQueryUseCase;
    private final CloneItineraryUseCase   cloneUseCase;

    // -------------------------------------------------------------------------
    // GET /  – list my itineraries
    // -------------------------------------------------------------------------

    @Operation(
        summary     = "List my itineraries",
        description = "Returns all itineraries owned by the authenticated user (filtered by userId from JWT). "
                    + "Anonymous users cannot use this endpoint - they must access itineraries via accessToken."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of itineraries"),
        @ApiResponse(responseCode = "401", description = "Authentication required",
                     content = @Content(schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class)))
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ItineraryDTO.ListResponse> listMyItineraries() {
        log.debug("GET /api/v1/itineraries");
        return ResponseEntity.ok(listUseCase.listMyItineraries());
    }

    // -------------------------------------------------------------------------
    // POST /  – create
    // -------------------------------------------------------------------------

    @Operation(
        summary     = "Create a new itinerary",
        description = "Accepts an itinerary creation request, persists it with QUEUED status, "
                    + "enqueues it for async enrichment, and returns a 202 Accepted acknowledgement."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Itinerary accepted and enqueued"),
        @ApiResponse(responseCode = "400", description = "Invalid request payload",
                     content = @Content(schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))),
        @ApiResponse(responseCode = "429", description = "Processing queue is full",
                     content = @Content(schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class)))
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ItineraryDTO.CreateResponse> create(
            @Valid @RequestBody ItineraryDTO.Request request) {

        log.debug("POST /api/v1/itineraries title='{}'", request.title());
        ItineraryDTO.CreateResponse response = createUseCase.create(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    // -------------------------------------------------------------------------
    // GET /{id}  – full view
    // -------------------------------------------------------------------------

    @Operation(
        summary     = "Get itinerary by ID",
        description = "Returns the full itinerary including all enriched steps, route geometry and AI suggestions."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Itinerary found"),
        @ApiResponse(responseCode = "403", description = "Invalid or missing access token",
                     content = @Content(schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Itinerary not found",
                     content = @Content(schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class)))
    })
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ItineraryDTO.Response> getById(
            @Parameter(description = "Itinerary UUID", required = true)
            @PathVariable UUID id,
            @Parameter(description = "Access token issued at creation time (required when not using JWT auth)")
            @RequestParam(value = "token", required = false) String token) {

        log.debug("GET /api/v1/itineraries/{}", id);
        return ResponseEntity.ok(getUseCase.getById(id, token));
    }

    // -------------------------------------------------------------------------
    // GET /{id}/status  – lightweight status
    // -------------------------------------------------------------------------

    @Operation(
        summary     = "Get itinerary processing status",
        description = "Returns a lightweight status snapshot suitable for polling while the itinerary is being processed."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Status retrieved"),
        @ApiResponse(responseCode = "403", description = "Invalid or missing access token",
                     content = @Content(schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Itinerary not found",
                     content = @Content(schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class)))
    })
    @GetMapping(value = "/{id}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ItineraryDTO.StatusResponse> getStatus(
            @Parameter(description = "Itinerary UUID", required = true)
            @PathVariable UUID id,
            @Parameter(description = "Access token issued at creation time (required when not using JWT auth)")
            @RequestParam(value = "token", required = false) String token) {

        log.debug("GET /api/v1/itineraries/{}/status", id);
        return ResponseEntity.ok(getUseCase.getStatus(id, token));
    }

    // -------------------------------------------------------------------------
    // PUT /{id}  – full replacement update
    // -------------------------------------------------------------------------

    @Operation(
        summary     = "Update an itinerary",
        description = "Replaces the mutable fields of an itinerary. "
                    + "If the itinerary is currently PROCESSING, the active job is interrupted and re-queued."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Itinerary updated"),
        @ApiResponse(responseCode = "400", description = "Invalid request payload",
                     content = @Content(schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Invalid or missing access token",
                     content = @Content(schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Itinerary not found",
                     content = @Content(schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class)))
    })
    @PutMapping(value = "/{id}",
                consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ItineraryDTO.Response> update(
            @Parameter(description = "Itinerary UUID", required = true)
            @PathVariable UUID id,
            @Parameter(description = "Access token issued at creation time", required = true)
            @RequestParam("token") String token,
            @Valid @RequestBody ItineraryDTO.Request request) {

        log.debug("PUT /api/v1/itineraries/{}", id);
        return ResponseEntity.ok(updateUseCase.update(id, token, request));
    }

    // -------------------------------------------------------------------------
    // DELETE /{id}  – delete
    // -------------------------------------------------------------------------

    @Operation(
        summary     = "Delete an itinerary",
        description = "Removes the itinerary from the processing queue (if present), "
                    + "cancels any active enrichment job, and deletes the entity."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Itinerary deleted"),
        @ApiResponse(responseCode = "403", description = "Invalid or missing access token",
                     content = @Content(schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Itinerary not found",
                     content = @Content(schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "Itinerary UUID", required = true)
            @PathVariable UUID id,
            @Parameter(description = "Access token issued at creation time (required when not using JWT auth)")
            @RequestParam(value = "token", required = false) String token) {

        log.debug("DELETE /api/v1/itineraries/{}", id);
        deleteUseCase.delete(id, token);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // PATCH /{id}/position  – update traveller position
    // -------------------------------------------------------------------------

    @Operation(
        summary     = "Update traveller position",
        description = "Advances the traveller's current step by explicit index or by nearest-coordinate match. "
                    + "Marks previous steps as VISITED and the current step as CURRENT."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Position updated"),
        @ApiResponse(responseCode = "400", description = "Invalid request payload",
                     content = @Content(schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Invalid or missing access token",
                     content = @Content(schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Itinerary not found",
                     content = @Content(schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class)))
    })
    @PatchMapping(value = "/{id}/position",
                  consumes = MediaType.APPLICATION_JSON_VALUE,
                  produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PositionUpdateDTO.Response> updatePosition(
            @Parameter(description = "Itinerary UUID", required = true)
            @PathVariable UUID id,
            @Parameter(description = "Access token issued at creation time (required when not using JWT auth)")
            @RequestParam(value = "token", required = false) String token,
            @Valid @RequestBody PositionUpdateDTO.Request request) {

        log.debug("PATCH /api/v1/itineraries/{}/position", id);
        return ResponseEntity.ok(positionUseCase.updatePosition(id, token, request));
    }

    // -------------------------------------------------------------------------
    // GET /{id}/saga  – get saga orchestration lifecycle
    // -------------------------------------------------------------------------

    @Operation(
        summary     = "Get saga orchestration lifecycle",
        description = "Returns saga orchestration lifecycle information for this itinerary. "
                    + "Shows the enrichment process state, completed steps, and any errors."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Saga information retrieved"),
        @ApiResponse(responseCode = "403", description = "Invalid or missing access token",
                     content = @Content(schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Itinerary not found",
                     content = @Content(schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class)))
    })
    @GetMapping(value = "/{id}/saga", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<SagaInstanceDto>> getSagaLifecycle(
            @Parameter(description = "Itinerary UUID", required = true)
            @PathVariable UUID id,
            @Parameter(description = "Access token issued at creation time (required when not using JWT auth)")
            @RequestParam(value = "token", required = false) String token) {

        log.debug("GET /api/v1/itineraries/{}/saga", id);
        
        // Verify access: either via access token or JWT
        // The getById method already validates access, so we'll reuse that logic
        getUseCase.getById(id, token); // This will throw if access is denied
        
        try {
            List<SagaInstanceDto> sagas = sagaQueryUseCase.getSagasByItinerary(id);
            return ResponseEntity.ok(sagas);
        } catch (Exception e) {
            log.error("Failed to fetch saga lifecycle for itinerary: {}", id, e);
            return ResponseEntity.status(500).build();
        }
    }

    // -------------------------------------------------------------------------
    // POST /{id}/clone  – clone itinerary
    // -------------------------------------------------------------------------

    @Operation(
        summary     = "Clone an existing itinerary",
        description = "Creates a copy of an existing itinerary with all its steps, "
                    + "persists it with QUEUED status, enqueues it for async enrichment, "
                    + "and returns a 202 Accepted acknowledgement."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Itinerary cloned and enqueued"),
        @ApiResponse(responseCode = "403", description = "Invalid or missing access token",
                     content = @Content(schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Source itinerary not found",
                     content = @Content(schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class))),
        @ApiResponse(responseCode = "429", description = "Processing queue is full",
                     content = @Content(schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class)))
    })
    @PostMapping(value = "/{id}/clone", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ItineraryDTO.CreateResponse> cloneItinerary(
            @Parameter(description = "Itinerary UUID to clone", required = true)
            @PathVariable UUID id,
            @Parameter(description = "Access token issued at creation time (required when not using JWT auth)")
            @RequestParam(value = "token", required = false) String token) {

        log.debug("POST /api/v1/itineraries/{}/clone", id);
        ItineraryDTO.CreateResponse response = cloneUseCase.cloneItinerary(id, token);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
