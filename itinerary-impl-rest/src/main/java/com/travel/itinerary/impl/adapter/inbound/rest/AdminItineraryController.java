package com.travel.itinerary.impl.adapter.inbound.rest;

import com.travel.itinerary.api.dto.AdminDTO;
import com.travel.itinerary.api.port.inbound.AdminItineraryUseCase;
import com.travel.itinerary.api.port.inbound.ItinerarySagaQueryUseCase;
import com.travel.saga.dto.SagaInstanceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin-only REST controller for itinerary management.
 * <p>
 * All endpoints require a valid JWT with {@code userType=ADMIN}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminItineraryController {

    private final AdminItineraryUseCase adminItineraryUseCase;
    private final ItinerarySagaQueryUseCase sagaQueryUseCase;

    // -------------------------------------------------------------------------
    // GET /api/v1/admin/itineraries
    // -------------------------------------------------------------------------

    /**
     * Returns a paginated list of all itineraries with owner information.
     * Only accessible to ADMIN users.
     *
     * @param page zero-based page number (default 0)
     * @param size page size (default 20, max 100)
     * @param auth injected JWT authentication token
     */
    @GetMapping("/itineraries")
    public ResponseEntity<AdminDTO.PagedItineraries> listAllItineraries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            JwtAuthenticationToken auth) {

        // ── Authorization: ADMIN users only ───────────────────────────────
        String userType = auth.getToken().getClaimAsString("userType");
        if (!"ADMIN".equals(userType)) {
            log.warn("Admin access denied for userType={}", userType);
            return ResponseEntity.status(403).build();
        }

        log.debug("Admin listing itineraries: page={} size={}", page, size);

        // Delegate to the use case (hexagonal architecture)
        AdminDTO.PagedItineraries result = adminItineraryUseCase.listAllItineraries(page, size);

        return ResponseEntity.ok(result);
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/admin/itineraries/{itineraryId}/saga
    // -------------------------------------------------------------------------

    /**
     * Returns saga orchestration lifecycle information for a specific itinerary.
     * Only accessible to ADMIN users.
     *
     * @param itineraryId the itinerary UUID
     * @param auth injected JWT authentication token
     */
    @GetMapping("/itineraries/{itineraryId}/saga")
    public ResponseEntity<List<SagaInstanceDto>> getItinerarySagaLifecycle(
            @PathVariable UUID itineraryId,
            JwtAuthenticationToken auth) {

        // ── Authorization: ADMIN users only ───────────────────────────────
        String userType = auth.getToken().getClaimAsString("userType");
        if (!"ADMIN".equals(userType)) {
            log.warn("Admin access denied for userType={}", userType);
            return ResponseEntity.status(403).build();
        }

        log.debug("Fetching saga lifecycle for itinerary: {}", itineraryId);

        try {
            List<SagaInstanceDto> sagas = sagaQueryUseCase.getSagasByItinerary(itineraryId);
            return ResponseEntity.ok(sagas);
        } catch (Exception e) {
            log.error("Failed to fetch saga lifecycle for itinerary: {}", itineraryId, e);
            return ResponseEntity.status(500).build();
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/v1/admin/saga/{sagaId}
    // -------------------------------------------------------------------------

    /**
     * Returns detailed saga instance information by saga ID.
     * Only accessible to ADMIN users.
     *
     * @param sagaId the saga UUID
     * @param auth injected JWT authentication token
     */
    @GetMapping("/saga/{sagaId}")
    public ResponseEntity<SagaInstanceDto> getSagaInstance(
            @PathVariable UUID sagaId,
            JwtAuthenticationToken auth) {

        // ── Authorization: ADMIN users only ───────────────────────────────
        String userType = auth.getToken().getClaimAsString("userType");
        if (!"ADMIN".equals(userType)) {
            log.warn("Admin access denied for userType={}", userType);
            return ResponseEntity.status(403).build();
        }

        log.debug("Fetching saga instance: {}", sagaId);

        try {
            return sagaQueryUseCase.getSagaInstance(sagaId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Failed to fetch saga instance: {}", sagaId, e);
            return ResponseEntity.status(500).build();
        }
    }
}
