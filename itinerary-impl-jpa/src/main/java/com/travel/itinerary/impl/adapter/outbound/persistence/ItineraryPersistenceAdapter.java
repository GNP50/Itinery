package com.travel.itinerary.impl.adapter.outbound.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.itinerary.api.dto.AdminDTO;
import com.travel.itinerary.api.dto.ItineraryDTO;
import com.travel.itinerary.api.dto.StepDTO;
import com.travel.itinerary.api.port.outbound.ItineraryPersistencePort;
import com.travel.itinerary.impl.domain.Itinerary;
import com.travel.itinerary.impl.domain.ItineraryStep;
import com.travel.itinerary.impl.exception.InvalidTokenException;
import com.travel.itinerary.impl.exception.ItineraryNotFoundException;
import com.travel.queue.port.QueueManagementPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound adapter that fulfils {@link ItineraryPersistencePort} by delegating
 * to a Spring Data JPA repository backed by PostgreSQL.
 * <p>
 * All public methods are executed within the caller's transaction context
 * ({@link Transactional#readOnly()} is set per-method to allow the JPA
 * provider to optimise flush behaviour for read-only operations).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ItineraryPersistenceAdapter implements ItineraryPersistencePort<Itinerary> {

    private final ItineraryJpaRepository itineraryRepo;
    private final ObjectMapper           objectMapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final QueueManagementPort    queueManagementPort;

    // -------------------------------------------------------------------------
    // Write operations
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public Itinerary save(Itinerary itinerary) {
        Itinerary saved = itineraryRepo.save(itinerary);
        log.debug("Persisted itinerary id={} status={}", saved.getId(), saved.getStatus());
        return saved;
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        itineraryRepo.deleteById(id);
        log.debug("Deleted itinerary id={}", id);
    }

    /**
     * Delete an itinerary identified by both its UUID and access token.
     * Throws {@link ItineraryNotFoundException} if the UUID is unknown, or
     * {@link InvalidTokenException} if the token does not match.
     *
     * @param id    itinerary UUID
     * @param token access token
     */
    @Transactional
    public void delete(UUID id, String token) {
        Itinerary entity = requireByIdAndToken(id, token);
        itineraryRepo.delete(entity);
        log.debug("Deleted itinerary id={} (token-verified)", id);
    }

    // -------------------------------------------------------------------------
    // Read operations
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Optional<Itinerary> findById(UUID id) {
        return itineraryRepo.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Itinerary> findByIdAndToken(UUID id, String token) {
        return itineraryRepo.findByIdAndAccessToken(id, token);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Itinerary> findAll() {
        return itineraryRepo.findAll();
    }

    /**
     * Finds a single itinerary step by its ID (optimized single-query lookup).
     *
     * @param stepId step UUID
     * @return optional step if found
     */
    @Transactional(readOnly = true)
    public Optional<ItineraryStep> findStepById(UUID stepId) {
        return itineraryRepo.findStepById(stepId);
    }

    /**
     * Finds all itineraries owned by the given user.
     *
     * @param userId owning user UUID
     * @return list of itineraries, never {@code null}
     */
    @Transactional(readOnly = true)
    public List<Itinerary> findByUserId(UUID userId) {
        return itineraryRepo.findByUserId(userId);
    }

    /**
     * Finds all itineraries owned by the given user with a specific status.
     * <p>
     * Performance-optimized query for queue status - avoids loading all queued items.
     *
     * @param userId owning user UUID
     * @param status itinerary status (expected to be ItineraryStatus enum)
     * @return list of itineraries matching both userId and status, never {@code null}
     */
    @Override
    @Transactional(readOnly = true)
    public List<Itinerary> findByOwnerIdAndStatus(UUID userId, Object status) {
        if (!(status instanceof com.travel.itinerary.impl.domain.enums.ItineraryStatus)) {
            throw new IllegalArgumentException("Status must be an ItineraryStatus enum");
        }
        return itineraryRepo.findByUserIdAndStatus(userId,
            (com.travel.itinerary.impl.domain.enums.ItineraryStatus) status);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByToken(String token) {
        return itineraryRepo.existsByAccessToken(token);
    }

    // -------------------------------------------------------------------------
    // Convenience methods – return DTOs for REST layer
    // -------------------------------------------------------------------------

    /**
     * Load an itinerary by UUID and token, then map it to a full
     * {@link ItineraryDTO.Response}.
     *
     * @param id    itinerary UUID
     * @param token access token
     * @return optional DTO response
     */
    @Transactional(readOnly = true)
    public Optional<ItineraryDTO.Response> findResponseByIdAndToken(UUID id, String token) {
        return itineraryRepo.findByIdAndAccessToken(id, token)
                            .map(this::toResponse);
    }

    /**
     * Load an itinerary by UUID only and map it to a full
     * {@link ItineraryDTO.Response}. Used for admin access without ownership check.
     *
     * @param id itinerary UUID
     * @return optional DTO response
     */
    @Transactional(readOnly = true)
    public Optional<ItineraryDTO.Response> findResponseById(UUID id) {
        return itineraryRepo.findById(id)
                            .map(this::toResponse);
    }

    /**
     * Load an itinerary by UUID and owning user UUID, then map it to a full
     * {@link ItineraryDTO.Response}.  Used for JWT-authenticated access.
     *
     * @param id     itinerary UUID
     * @param userId owning user UUID
     * @return optional DTO response
     */
    @Transactional(readOnly = true)
    public Optional<ItineraryDTO.Response> findResponseByIdAndUser(UUID id, UUID userId) {
        return itineraryRepo.findByIdAndUserId(id, userId)
                            .map(this::toResponse);
    }

    /**
     * Load all itineraries and map them to full {@link ItineraryDTO.Response} objects.
     * Uses findAllWithSteps() to eagerly load steps and avoid N+1 queries.
     *
     * @return list of DTO responses, never {@code null}
     */
    @Transactional(readOnly = true)
    public List<ItineraryDTO.Response> findAllResponses() {
        return itineraryRepo.findAllWithSteps()
                            .stream()
                            .map(this::toResponse)
                            .toList();
    }

    // -------------------------------------------------------------------------
    // Admin operations
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public AdminDTO.PagedItineraries findAllItinerariesWithOwner(int page, int size) {
        int pageSize = Math.min(Math.max(size, 1), 100);
        int pageNum  = Math.max(page, 0);

        // Count total
        long totalElements = itineraryRepo.countAllItineraries();
        int  totalPages    = pageSize > 0 ? (int) Math.ceil((double) totalElements / pageSize) : 0;

        // Fetch page with owner join (without queue_position from DB)
        PageRequest pageable = PageRequest.of(pageNum, pageSize);
        List<Object[]> results = itineraryRepo.findAllWithOwnerNative(pageable);

        // Map Object[] to AdminDTO.ItinerarySummary and fetch queue positions from Redis
        List<AdminDTO.ItinerarySummary> items = results.stream()
            .map(row -> {
                UUID itineraryId = (UUID) row[0];

                // Fetch queue position from Redis (source of truth)
                int queuePosition = queueManagementPort.getPositionInQueue(itineraryId);
                Integer queuePos = queuePosition > 0 ? queuePosition : null;

                return new AdminDTO.ItinerarySummary(
                    itineraryId,                                          // id
                    (String) row[1],                                      // title
                    (String) row[2],                                      // status
                    (UUID) row[3],                                        // userId
                    (String) row[4],                                      // email
                    (String) row[5],                                      // name
                    (String) row[6],                                      // userType
                    row[7] != null ? (java.time.Instant) row[7] : null,  // createdAt
                    queuePos,                                             // queuePosition (from Redis)
                    (String) row[8]                                       // accessToken
                );
            })
            .toList();

        log.debug("Admin query: fetched {} itineraries, page={} size={} total={}",
                  items.size(), pageNum, pageSize, totalElements);

        return new AdminDTO.PagedItineraries(
            items, totalElements, totalPages, pageNum, pageSize
        );
    }

    // -------------------------------------------------------------------------
    // Mapping
    // -------------------------------------------------------------------------

    /**
     * Maps a {@link Itinerary} domain entity to a {@link ItineraryDTO.Response}.
     *
     * @param entity the entity to map; must not be {@code null}
     * @return fully populated response DTO
     */
    private ItineraryDTO.Response toResponse(Itinerary entity) {
        List<StepDTO.Response> stepResponses = entity.getSteps()
                                                     .stream()
                                                     .map(this::toStepResponse)
                                                     .toList();

        Map<String, Object> preferencesMap = parseJsonToMap(entity.getPreferences());
        Map<String, Object> aiSuggestionsMap = parseJsonToMap(entity.getAiSuggestions());

        return new ItineraryDTO.Response(
            entity.getId(),
            entity.getAccessToken(),
            entity.getTitle(),
            entity.getDescription(),
            entity.getStatus() != null ? entity.getStatus().name() : null,
            entity.getQueuePosition(),
            entity.getEstimatedCompletion() != null ? entity.getEstimatedCompletion().toString() : null,
            entity.getTravelMode() != null ? entity.getTravelMode().name() : null,
            entity.getTotalDistanceKm(),
            entity.getTotalDurationMinutes(),
            entity.getCurrentStepIndex(),
            stepResponses,
            preferencesMap,
            aiSuggestionsMap,
            entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null,
            entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null
        );
    }

    /**
     * Maps a {@link ItineraryStep} entity to a {@link StepDTO.Response}.
     *
     * @param step the step entity; must not be {@code null}
     * @return populated step DTO
     */
    private StepDTO.Response toStepResponse(ItineraryStep step) {
        Object routeGeometry = parseJsonToObject(step.getRouteGeometryFromPrev());
        Object poiNearby     = parseJsonToObject(step.getPoiNearby());
        Map<String, Object> preferences = parseJsonToMap(step.getPreferences());

        return new StepDTO.Response(
            step.getId(),
            step.getStepOrder(),
            step.getPlaceName(),
            step.getCity(),
            step.getProvince(),
            step.getRegion(),
            step.getCountry(),
            step.getCountryCode(),
            step.getLatitude(),
            step.getLongitude(),
            step.getOsmId(),
            step.getNotes(),
            step.getAiDescription(),
            step.getAiTips(),
            step.getDistanceFromPrevKm(),
            step.getDurationFromPrevMin(),
            routeGeometry,
            poiNearby,
            step.getStatus() != null ? step.getStatus().name() : null,
            step.getArrivalDate() != null ? step.getArrivalDate().toString() : null,
            preferences
        );
    }

    // -------------------------------------------------------------------------
    // JSON helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonToMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            log.warn("Failed to parse JSON map: {}", ex.getMessage());
            return Map.of();
        }
    }

    private Object parseJsonToObject(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception ex) {
            log.warn("Failed to parse JSON object: {}", ex.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Private guard helpers
    // -------------------------------------------------------------------------

    private Itinerary requireByIdAndToken(UUID id, String token) {
        Itinerary entity = itineraryRepo.findById(id)
            .orElseThrow(() -> new ItineraryNotFoundException(
                "Itinerary not found: " + id));
        if (!entity.getAccessToken().equals(token)) {
            throw new InvalidTokenException(
                "Invalid access token for itinerary: " + id);
        }
        return entity;
    }
}
