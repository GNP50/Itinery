package com.travel.itinerary.impl.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.itinerary.api.dto.AdminDTO;
import com.travel.itinerary.api.dto.ItineraryDTO;
import com.travel.itinerary.api.dto.PositionUpdateDTO;
import com.travel.queue.dto.QueueStatusDTO;
import com.travel.itinerary.api.dto.StepDTO;
import com.travel.itinerary.api.event.ItineraryCreatedEvent;
import com.travel.itinerary.api.event.ItineraryUpdatedEvent;
import com.travel.itinerary.api.port.inbound.*;
import com.travel.itinerary.api.port.inbound.CloneItineraryUseCase;
import com.travel.itinerary.api.port.outbound.EventPublisherPort;
import com.travel.itinerary.api.port.outbound.ItineraryPersistencePort;
import com.travel.queue.port.QueueManagementPort;
import com.travel.queue.port.QueuePort;
import com.travel.itinerary.api.port.outbound.SagaQueryPort;
import com.travel.itinerary.impl.domain.Itinerary;
import com.travel.itinerary.impl.domain.ItineraryStep;
import com.travel.itinerary.impl.annotation.RateLimit;
import com.travel.itinerary.impl.domain.enums.ItineraryStatus;
import com.travel.itinerary.impl.domain.enums.StepStatus;
import com.travel.itinerary.impl.domain.enums.TravelMode;
import com.travel.itinerary.impl.exception.AuthenticationRequiredException;
import com.travel.itinerary.impl.exception.InvalidTokenException;
import com.travel.itinerary.impl.exception.ItineraryNotFoundException;
import com.travel.itinerary.impl.exception.QueueFullException;
import com.travel.saga.dto.SagaInstanceDto;
import com.travel.user.api.dto.AuthDTO;
import com.travel.user.api.port.inbound.AuthUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service implementing all inbound use-case ports for itinerary
 * management.
 * <p>
 * This class orchestrates the persistence, queue, and event-publishing adapters
 * to fulfil the following use cases:
 * <ul>
 *   <li>{@link CreateItineraryUseCase} – accept, persist, enqueue and acknowledge</li>
 *   <li>{@link GetItineraryUseCase}    – retrieve full view or lightweight status</li>
 *   <li>{@link UpdateItineraryUseCase} – replace fields and re-queue if needed</li>
 *   <li>{@link DeleteItineraryUseCase} – remove from queue then delete entity</li>
 *   <li>{@link UpdatePositionUseCase}  – advance the traveller's current step</li>
 *   <li>{@link QueryQueueUseCase}      – query queue status</li>
 * </ul>
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ItineraryService
        implements CreateItineraryUseCase,
                   GetItineraryUseCase,
                   UpdateItineraryUseCase,
                   DeleteItineraryUseCase,
                   UpdatePositionUseCase,
                   QueryQueueUseCase,
                   AdminItineraryUseCase,
                   ListItinerariesUseCase,
                   ItinerarySagaQueryUseCase,
                   CloneItineraryUseCase {

    private final ItineraryPersistencePort<Itinerary> persistencePort;
    private final QueuePort<UUID>                     queuePort;
    private final EventPublisherPort                  eventPublisher;
    private final AuthUseCase                         authUseCase;
    private final QueueManagementPort                 queueManagementPort;
    private final ObjectMapper                        objectMapper;
    private final SagaQueryPort                       sagaQueryPort;
    // -------------------------------------------------------------------------
    // CreateItineraryUseCase
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     * <p>
     * Creates the itinerary with {@code QUEUED} status, persists it, enqueues
     * its UUID in Redis, and publishes an {@link ItineraryCreatedEvent}.
     *
     * @throws QueueFullException if the queue has already reached maximum capacity
     */
    @Override
    @RateLimit(requests = 5, windowSeconds = 3600, key = "itinerary_create")
    public ItineraryDTO.CreateResponse create(ItineraryDTO.Request request) {
        log.info("Creating itinerary title='{}' travelMode={}", request.title(), request.travelMode());

        // Validate queue capacity before persisting
        QueueStatusDTO.Response queueStatus = queueManagementPort.getQueueStatus();
        if (queueStatus.totalQueueLength() >= queueManagementPort.getMaxQueueSize()) {
            throw new QueueFullException(
                "Processing queue is full (" + queueStatus.totalQueueLength() + " items). Please try again later.");
        }

        // Require authentication (Spring Security enforces this at the HTTP level,
        // but we double-check here to obtain the userId for ownership tracking)
        UUID userId = authUseCase.getCurrentUserId()
            .orElseThrow(() -> new AuthenticationRequiredException("Authentication required to create an itinerary"));

        // Resolve travel mode
        TravelMode travelMode = resolveTravelMode(request.travelMode());

        // Serialise preferences to JSON
        String preferencesJson = serializeToJson(request.preferences());

        // Build aggregate via package-private factory
        Itinerary itinerary = Itinerary.create(
            request.title(),
            request.description(),
            travelMode,
            preferencesJson,
            userId
        );

        // Attach steps
        if (request.steps() != null) {
            for (int i = 0; i < request.steps().size(); i++) {
                StepDTO.Request stepReq = request.steps().get(i);
                LocalDate arrivalDate = parseLocalDate(stepReq.arrivalDate());
                ItineraryStep step = ItineraryStep.create(i + 1, stepReq.placeName(), stepReq.notes(), arrivalDate);
                
                // Serialize step-level preferences if present
                if (stepReq.preferences() != null && !stepReq.preferences().isEmpty()) {
                    step.setPreferences(serializeToJson(stepReq.preferences()));
                }
                
                itinerary.addStep(step);
            }
        }

        // Persist
        Itinerary saved = persistencePort.save(itinerary);

        // Enqueue with priority based on user type
        AuthDTO.StatusResponse authStatus = authUseCase.getStatus();
        String userType = authStatus.userType();
        int queuePosition = queueManagementPort.enqueue(saved.getId(), userType);

        log.debug("Enqueued itinerary id={} to {} queue at position={}",
                  saved.getId(), userType, queuePosition);

        saved.setQueuePosition(queuePosition);
        saved = persistencePort.save(saved);

        // Collect step place names for Saga Orchestrator worker dispatch
        List<String> stepPlaceNames = saved.getSteps().stream()
            .map(ItineraryStep::getPlaceName)
            .toList();

        // Publish domain event (routed to itinerary.created via OutboxEventPublisherAdapter)
        ItineraryCreatedEvent event = new ItineraryCreatedEvent(
            saved.getId(),
            saved.getAccessToken(),
            userType,  // Include user type for saga priority processing
            saved.getTitle(),
            saved.getTravelMode() != null ? saved.getTravelMode().name() : null,
            saved.getSteps().size(),
            stepPlaceNames,
            preferencesJson,
            UUID.randomUUID().toString(),  // correlationId
            Instant.now()
        );
        eventPublisher.publish(event);

        log.info("Itinerary created id={} queuePosition={}", saved.getId(), queuePosition);

        return new ItineraryDTO.CreateResponse(
            saved.getId(),
            saved.getAccessToken(),
            saved.getStatus().name(),
            queuePosition,
            saved.getEstimatedCompletion() != null ? saved.getEstimatedCompletion().toString() : null
        );
    }

    // -------------------------------------------------------------------------
    // GetItineraryUseCase
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * @throws ItineraryNotFoundException if no itinerary exists with the given id
     * @throws InvalidTokenException      if the token does not match the itinerary
     */
    @Override
    @Transactional(readOnly = true)
    public ItineraryDTO.Response getById(UUID id, String token) {
        log.debug("getById id={}", id);

        // Authenticated users access by ownership (or admin access)
        ItineraryDTO.Response baseResponse;
        Optional<UUID> userId = authUseCase.getCurrentUserId();
        if (userId.isPresent()) {
            // Check if user is admin
            AuthDTO.StatusResponse authStatus = authUseCase.getStatus();
            String userType = authStatus.userType();
            
            if ("ADMIN".equalsIgnoreCase(userType)) {
                // Admin can access any itinerary without ownership check
                baseResponse = persistencePort.findResponseById(id)
                    .orElseThrow(() -> new ItineraryNotFoundException("Itinerary not found: " + id));
            } else {
                // Regular users access by ownership
                baseResponse = persistencePort.findResponseByIdAndUser(id, userId.get())
                    .orElseThrow(() -> new ItineraryNotFoundException("Itinerary not found: " + id));
            }
        } else if (token != null && !token.isBlank()) {
            // Anonymous / shared access via accessToken query param
            baseResponse = persistencePort.findResponseByIdAndToken(id, token)
                .orElseThrow(() -> resolveNotFoundException(id, token));
        } else {
            throw new InvalidTokenException("Access token required");
        }

        // Sync queue position from Redis for items in queue (QUEUED or PROCESSING)
        // ALWAYS query Redis (source of truth) for accurate position
        if ("QUEUED".equals(baseResponse.status()) || "PROCESSING".equals(baseResponse.status())) {
            int livePosition = queueManagementPort.getPositionInQueue(id);
            if (livePosition != -1 && livePosition != baseResponse.queuePosition()) {
                log.debug("Syncing queue position for id={} status={}: DB={} -> Redis={}",
                        id, baseResponse.status(), baseResponse.queuePosition(), livePosition);

                // Create updated response with live position
                return new ItineraryDTO.Response(
                    baseResponse.id(),
                    baseResponse.accessToken(),
                    baseResponse.title(),
                    baseResponse.description(),
                    baseResponse.status(),
                    livePosition,  // UPDATED: Live position from Redis
                    baseResponse.estimatedCompletion(),
                    baseResponse.travelMode(),
                    baseResponse.totalDistanceKm(),
                    baseResponse.totalDurationMinutes(),
                    baseResponse.currentStepIndex(),
                    baseResponse.steps(),
                    baseResponse.preferences(),
                    baseResponse.aiSuggestions(),
                    baseResponse.createdAt(),
                    baseResponse.updatedAt()
                );
            } else if (livePosition == -1) {
                log.warn("Item marked {} but not found in Redis queue: id={}, using DB position={}",
                        baseResponse.status(), id, baseResponse.queuePosition());
            }
        }
        
        return baseResponse;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a lightweight status snapshot including queue position and progress
     * percent (computed from completed-step count / total-step count).
     * <p>
     * If latitude and longitude are provided, the current position is automatically
     * updated to the nearest step before returning the status (similar to the
     * updatePosition use case but without requiring a separate API call).
     *
     * @throws ItineraryNotFoundException if no itinerary exists with the given id
     * @throws InvalidTokenException      if the token does not match the itinerary
     */
    @Override
    @Transactional(readOnly = true)
    public ItineraryDTO.StatusResponse getStatus(UUID id, String token) {
        log.debug("getStatus id={}", id);

        Itinerary itinerary = requireWithAccess(id, token);

        // COMPLETED means the full enrichment pipeline ran — always 100%
        // Otherwise count how many steps the traveller has visited
        int progressPercent;
        if (itinerary.getStatus() == ItineraryStatus.COMPLETED) {
            progressPercent = 100;
        } else {
            int totalSteps     = itinerary.getSteps().size();
            int completedSteps = (int) itinerary.getSteps().stream()
                .filter(s -> s.getStatus() == StepStatus.VISITED || s.getStatus() == StepStatus.CURRENT)
                .count();
            progressPercent = totalSteps > 0 ? (completedSteps * 100 / totalSteps) : 0;
        }

        // Sync queue position from Redis for items in queue (QUEUED or PROCESSING)
        // ALWAYS query Redis (source of truth) for accurate position
        Integer queuePosition = itinerary.getQueuePosition();
        Integer dbPosition = queuePosition; // Save for logging

        if (itinerary.getStatus() == ItineraryStatus.QUEUED || itinerary.getStatus() == ItineraryStatus.PROCESSING) {
            int livePosition = queueManagementPort.getPositionInQueue(id);
            if (livePosition != -1) {
                queuePosition = livePosition;
                log.debug("Synced queue position for id={} status={}: DB={} -> Redis={}",
                         id, itinerary.getStatus(), dbPosition, livePosition);
            } else {
                log.warn("Item marked {} but not found in Redis queue: id={}, using DB position={}",
                        itinerary.getStatus(), id, dbPosition);
            }
        } else {
            // COMPLETED or FAILED - no queue position
            queuePosition = null;
            log.debug("Item not in queue (status={}), no queue position", itinerary.getStatus());
        }

        return new ItineraryDTO.StatusResponse(
            itinerary.getId(),
            itinerary.getStatus().name(),
            queuePosition,  // Live position from Redis when QUEUED, otherwise DB position
            itinerary.getEstimatedCompletion() != null ? itinerary.getEstimatedCompletion().toString() : null,
            progressPercent
        );
    }

    // -------------------------------------------------------------------------
    // UpdateItineraryUseCase
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     * <p>
     * If the itinerary is currently {@code PROCESSING}, the cancellation is
     * attempted via {@link QueueManagementPort#cancelProcessing(UUID)} which
     * interrupts the processing task.
     *
     * @throws ItineraryNotFoundException if no itinerary exists with the given id
     * @throws InvalidTokenException      if the token does not match the itinerary
     */
    @Override
    @RateLimit(requests = 10, windowSeconds = 3600, key = "itinerary_update")
    public ItineraryDTO.Response update(UUID id, String token, ItineraryDTO.Request request) {
        log.info("Updating itinerary id={}", id);

        Itinerary itinerary = requireWithAccess(id, token);

        boolean wasProcessing = itinerary.getStatus() == ItineraryStatus.PROCESSING;
        boolean wasQueued     = itinerary.getStatus() == ItineraryStatus.QUEUED;

        // Interrupt active processing if running
        if (wasProcessing) {
            queueManagementPort.cancelProcessing(id);
        }

        // Remove from queue if present
        if (wasQueued || wasProcessing) {
            queuePort.remove(id);
        }

        // Apply updates
        itinerary.setTitle(request.title());
        itinerary.setDescription(request.description());
        if (request.travelMode() != null) {
            itinerary.setTravelMode(resolveTravelMode(request.travelMode()));
        }
        if (request.preferences() != null) {
            itinerary.setPreferences(serializeToJson(request.preferences()));
        }

        // Replace steps
        if (request.steps() != null) {
            List<ItineraryStep> newSteps = request.steps().stream()
                .map((StepDTO.Request stepReq) -> {
                    int idx = request.steps().indexOf(stepReq);
                    LocalDate arrivalDate = parseLocalDate(stepReq.arrivalDate());
                    ItineraryStep step = ItineraryStep.create(idx + 1, stepReq.placeName(), stepReq.notes(), arrivalDate);
                    
                    // Serialize step-level preferences if present
                    if (stepReq.preferences() != null && !stepReq.preferences().isEmpty()) {
                        step.setPreferences(serializeToJson(stepReq.preferences()));
                    }
                    
                    return step;
                })
                .toList();
            itinerary.replaceSteps(newSteps);
        }

        // Re-queue with priority based on user type
        itinerary.setStatus(ItineraryStatus.QUEUED);
        AuthDTO.StatusResponse authStatus = authUseCase.getStatus();
        String userType = authStatus.userType();
        int newQueuePosition = queueManagementPort.enqueue(itinerary.getId(), userType);

        itinerary.setQueuePosition(newQueuePosition);

        Itinerary saved = persistencePort.save(itinerary);
        log.info("Itinerary updated id={} re-queued to {} queue at position={}",
                 id, userType, newQueuePosition);

        // Publish ItineraryUpdatedEvent to trigger saga restart
        List<String> stepPlaceNames = saved.getSteps().stream()
            .map(ItineraryStep::getPlaceName)
            .toList();
        
        ItineraryUpdatedEvent event = new ItineraryUpdatedEvent(
            saved.getId(),
            saved.getTitle(),
            saved.getTravelMode().name(),
            saved.getSteps().size(),
            stepPlaceNames,
            saved.getPreferences(),
            userType,
            UUID.randomUUID().toString(),
            Instant.now()
        );
        eventPublisher.publish(event);
        log.debug("Published ItineraryUpdatedEvent for itinerary id={}", saved.getId());

        // Return the updated itinerary: prefer JWT-based lookup, fall back to token
        Optional<UUID> currentUserId = authUseCase.getCurrentUserId();
        if (currentUserId.isPresent()) {
            return persistencePort.findResponseByIdAndUser(saved.getId(), currentUserId.get())
                .orElseThrow(() -> new ItineraryNotFoundException("Itinerary not found after update: " + id));
        }
        return persistencePort.findResponseByIdAndToken(saved.getId(), token)
            .orElseThrow(() -> new ItineraryNotFoundException("Itinerary not found after update: " + id));
    }

    // -------------------------------------------------------------------------
    // DeleteItineraryUseCase
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     * <p>
     * Removes the itinerary from the queue (if present), cancels any active
     * processing, then deletes the entity and all child steps.
     *
     * @throws ItineraryNotFoundException if no itinerary exists with the given id
     * @throws InvalidTokenException      if the token does not match the itinerary
     */
    @Override
    public void delete(UUID id, String token) {
        log.info("Deleting itinerary id={}", id);

        Itinerary itinerary = requireWithAccess(id, token);

        // Cancel active processing if running
        if (itinerary.getStatus() == ItineraryStatus.PROCESSING) {
            queueManagementPort.cancelProcessing(id);
        }

        // Remove from queue
        queuePort.remove(id);

        // Delete entity (cascades to steps via orphan removal)
        persistencePort.delete(id);
        log.info("Itinerary deleted id={}", id);
    }

    // -------------------------------------------------------------------------
    // UpdatePositionUseCase
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     * <p>
     * If {@code request.currentStepIndex()} is provided, the step index is set
     * directly.  Otherwise, if lat/lon coordinates are provided, the nearest step
     * is found using the Haversine approximation and that step's index is used.
     * <p>
     * All steps before the new index are marked {@code VISITED}; the step at the
     * new index is marked {@code CURRENT}; subsequent steps remain {@code PENDING}.
     *
     * @throws ItineraryNotFoundException if no itinerary exists with the given id
     * @throws InvalidTokenException      if the token does not match the itinerary
     */
    @Override
    public PositionUpdateDTO.Response updatePosition(UUID id, String token, PositionUpdateDTO.Request request) {
        log.debug("updatePosition id={} stepIndex={} lat={} lon={}",
                  id, request.currentStepIndex(), request.latitude(), request.longitude());

        Itinerary itinerary = requireWithAccess(id, token);
        List<ItineraryStep> steps = itinerary.getSteps();

        int targetIndex;

        if (request.currentStepIndex() != null) {
            // Direct step index provided
            targetIndex = request.currentStepIndex();
        } else if (request.latitude() != null && request.longitude() != null) {
            // Find nearest step by coordinate proximity
            targetIndex = findNearestStepIndex(steps, request.latitude(), request.longitude());
        } else {
            // No position information – keep current
            targetIndex = itinerary.getCurrentStepIndex() != null ? itinerary.getCurrentStepIndex() : 0;
        }

        // Clamp to valid range
        targetIndex = Math.max(0, Math.min(targetIndex, steps.size() - 1));

        // Update step statuses
        for (int i = 0; i < steps.size(); i++) {
            ItineraryStep step = steps.get(i);
            if (i < targetIndex) {
                step.setStatus(StepStatus.VISITED);
            } else if (i == targetIndex) {
                step.setStatus(StepStatus.CURRENT);
            }
            // Steps after targetIndex keep their existing status (PENDING or as set by processor)
        }

        itinerary.setCurrentStepIndex(targetIndex);
        Itinerary saved = persistencePort.save(itinerary);

        return new PositionUpdateDTO.Response(
            saved.getId(),
            saved.getCurrentStepIndex(),
            saved.getUpdatedAt() != null ? saved.getUpdatedAt().toString() : Instant.now().toString()
        );
    }

    // -------------------------------------------------------------------------
    // QueryQueueUseCase
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     * <p>
     * Retrieves queue statistics and enriches them with itinerary details.
     * Only includes items owned by the current user for privacy.
     * <p>
     * Performance optimized: Queries DB for user's QUEUED items first (typically 1-5),
     * then fetches positions from Redis. Scales with user's items, not total queue size.
     */
    @Override
    @Transactional(readOnly = true)
    public QueueStatusDTO.Response getQueueStatus() {
        // Get basic statistics from queue management port
        QueueStatusDTO.Response baseStatus = queueManagementPort.getQueueStatus();

        // Get current user ID (will be present for both registered and anonymous users via JWT)
        Optional<UUID> currentUserId = authUseCase.getCurrentUserId();

        if (currentUserId.isEmpty()) {
            // No authenticated user - return empty items list
            log.debug("No authenticated user, returning empty queue items");
            return new QueueStatusDTO.Response(
                baseStatus.totalQueueLength(),
                baseStatus.registeredQueueSize(),
                baseStatus.anonymousQueueSize(),
                List.of(),
                baseStatus.maxQueueSize(),
                baseStatus.maxConcurrent(),
                baseStatus.processingCount()
            );
        }

        UUID userId = currentUserId.get();

        // PERFORMANCE OPTIMIZATION: Query DB for user's QUEUED itineraries only
        // This avoids loading all queue items - typically returns 1-5 items per user
        List<Itinerary> userQueuedItineraries = persistencePort.findByOwnerIdAndStatus(
            userId,
            ItineraryStatus.QUEUED
        );

        // Build queue items with position from Redis
        List<QueueStatusDTO.QueueItem> items = new ArrayList<>();
        for (Itinerary itinerary : userQueuedItineraries) {
            try {
                // Get accurate position from Redis (O(log N) operation)
                int position = queueManagementPort.getPositionInQueue(itinerary.getId());

                // Skip if not in queue (edge case: status inconsistency)
                if (position < 0) {
                    log.warn("Itinerary marked QUEUED but not in Redis queue: id={}", itinerary.getId());
                    continue;
                }

                // Calculate estimated completion based on position
                // Assuming 30 seconds per itinerary processing time
                int estimatedSeconds = position * 30;
                String estimatedCompletion = Instant.now()
                    .plusSeconds(estimatedSeconds)
                    .toString();

                QueueStatusDTO.QueueItem item = new QueueStatusDTO.QueueItem(
                    itinerary.getId(),
                    itinerary.getTitle(),
                    itinerary.getStatus().name(),
                    position,
                    estimatedCompletion
                );
                items.add(item);
            } catch (Exception e) {
                log.warn("Failed to get queue position for itinerary: id={}", itinerary.getId(), e);
            }
        }

        // Sort by position (ascending)
        items.sort(Comparator.comparing(QueueStatusDTO.QueueItem::queuePosition));

        // Return enhanced response with items (filtered by user)
        return new QueueStatusDTO.Response(
            baseStatus.totalQueueLength(),
            baseStatus.registeredQueueSize(),
            baseStatus.anonymousQueueSize(),
            items,
            baseStatus.maxQueueSize(),
            baseStatus.maxConcurrent(),
            baseStatus.processingCount()
        );
    }

    // -------------------------------------------------------------------------
    // ListItinerariesUseCase
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     * <p>
     * Returns all itineraries owned by the authenticated user (extracted from JWT).
     * Anonymous users cannot use this endpoint - they must access itineraries via
     * the accessToken of each individual itinerary.
     *
     * @throws AuthenticationRequiredException if no authenticated user is found in the security context
     */
    @Override
    @Transactional(readOnly = true)
    public ItineraryDTO.ListResponse listMyItineraries() {
        UUID userId = authUseCase.getCurrentUserId()
            .orElseThrow(() -> new AuthenticationRequiredException("Authentication required to list itineraries"));

        List<Itinerary> itineraries = persistencePort.findByUserId(userId);

        List<ItineraryDTO.ItinerarySummary> summaries = itineraries.stream()
            .map(this::toSummary)
            .toList();

        log.debug("Listed {} itineraries for user id={}", summaries.size(), userId);
        return new ItineraryDTO.ListResponse(summaries);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Loads an itinerary and verifies access via JWT ownership or access token.
     * <p>
     * Access is granted when either:
     * <ol>
     *   <li>The current JWT's userId matches {@code itinerary.userId} (owner access), or</li>
     *   <li>The supplied {@code token} matches the itinerary's {@code accessToken} (sharing).</li>
     * </ol>
     */
    private Itinerary requireWithAccess(UUID id, String token) {
        Itinerary itinerary = persistencePort.findById(id)
            .orElseThrow(() -> new ItineraryNotFoundException("Itinerary not found: " + id));

        Optional<UUID> currentUserId = authUseCase.getCurrentUserId();

        if (currentUserId.isPresent() && currentUserId.get().equals(itinerary.getUserId())) {
            return itinerary;
        }

        if (token != null && !token.isBlank() && itinerary.getAccessToken().equals(token)) {
            return itinerary;
        }

        throw new InvalidTokenException("Access denied for itinerary: " + id);
    }

    /**
     * Determines whether the itinerary was not found (id unknown) or just had a
     * bad token, to produce the correct domain exception.
     */
    private RuntimeException resolveNotFoundException(UUID id, String token) {
        if (persistencePort.findById(id).isPresent()) {
            return new InvalidTokenException("Invalid access token for itinerary: " + id);
        }
        return new ItineraryNotFoundException("Itinerary not found: " + id);
    }

    /**
     * Parses a travel mode string case-insensitively, defaulting to {@code CAR}
     * when the value is blank or unrecognised.
     */
    private TravelMode resolveTravelMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return TravelMode.CAR;
        }
        try {
            return TravelMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown travel mode '{}', defaulting to CAR", mode);
            return TravelMode.CAR;
        }
    }

    /**
     * Serialises an object to a compact JSON string, returning {@code null} on
     * failure.
     */
    private String serializeToJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialise object to JSON: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * Parses an ISO-8601 local date string, returning {@code null} for blank or
     * unparseable input.
     */
    private LocalDate parseLocalDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr);
        } catch (Exception ex) {
            log.warn("Could not parse arrival date '{}': {}", dateStr, ex.getMessage());
            return null;
        }
    }

    /**
     * Returns the 0-based index of the step whose coordinates are closest to the
     * supplied {@code lat}/{@code lon} using an equirectangular-approximation
     * distance formula.  Falls back to index 0 when no step has coordinates.
     */
    private int findNearestStepIndex(List<ItineraryStep> steps, BigDecimal lat, BigDecimal lon) {
        double targetLat = lat.doubleValue();
        double targetLon = lon.doubleValue();
        int    nearestIdx  = 0;
        double nearestDist = Double.MAX_VALUE;

        for (int i = 0; i < steps.size(); i++) {
            ItineraryStep step = steps.get(i);
            if (step.getLatitude() == null || step.getLongitude() == null) {
                continue;
            }
            double dLat  = Math.toRadians(step.getLatitude().doubleValue() - targetLat);
            double dLon  = Math.toRadians(step.getLongitude().doubleValue() - targetLon);
            double meanLat = Math.toRadians((step.getLatitude().doubleValue() + targetLat) / 2.0);
            // Equirectangular approximation (sufficient for proximity ranking)
            double dist  = Math.sqrt(dLat * dLat + Math.pow(Math.cos(meanLat) * dLon, 2));
            if (dist < nearestDist) {
                nearestDist = dist;
                nearestIdx  = i;
            }
        }
        return nearestIdx;
    }

    /**
     * Converts a domain Itinerary to a lightweight DTO summary.
     */
    private ItineraryDTO.ItinerarySummary toSummary(Itinerary it) {
        return new ItineraryDTO.ItinerarySummary(
            it.getId(),
            it.getTitle(),
            it.getStatus() != null ? it.getStatus().name() : "UNKNOWN",
            it.getTravelMode() != null ? it.getTravelMode().name() : "CAR",
            it.getSteps() != null ? it.getSteps().size() : 0,
            it.getCreatedAt() != null ? it.getCreatedAt().toString() : Instant.now().toString()
        );
    }

    // -------------------------------------------------------------------------
    // AdminItineraryUseCase
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     * <p>
     * Returns a paginated list of all itineraries in the system with owner
     * information. This delegates to the persistence port which performs a
     * JOIN with the users table.
     */
    @Override
    @Transactional(readOnly = true)
    public AdminDTO.PagedItineraries listAllItineraries(int page, int size) {
        log.debug("Admin listing all itineraries: page={} size={}", page, size);
        return persistencePort.findAllItinerariesWithOwner(page, size);
    }

    // -------------------------------------------------------------------------
    // ItinerarySagaQueryUseCase
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     * <p>
     * Delegates to the saga query port adapter (typically a gRPC client) to
     * retrieve all saga instances associated with the given itinerary.
     */
    @Override
    @Transactional(readOnly = true)
    public List<SagaInstanceDto> getSagasByItinerary(UUID itineraryId) {
        log.debug("Fetching sagas for itinerary: {}", itineraryId);
        return sagaQueryPort.getSagasByItinerary(itineraryId);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Delegates to the saga query port adapter to retrieve a specific saga instance.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<SagaInstanceDto> getSagaInstance(UUID sagaId) {
        log.debug("Fetching saga instance: {}", sagaId);
        return sagaQueryPort.getSagaInstance(sagaId);
    }

    // -------------------------------------------------------------------------
    // CloneItineraryUseCase
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     * <p>
     * Clones an existing itinerary with all its steps, creates a new entity
     * with QUEUED status, enqueues it for processing, and publishes an event.
     *
     * @throws ItineraryNotFoundException if source itinerary doesn't exist
     * @throws InvalidTokenException      if token doesn't match the source itinerary
     * @throws QueueFullException         if the queue has reached maximum capacity
     */
    @Override
    @RateLimit(requests = 3, windowSeconds = 3600, key = "itinerary_clone")
    public ItineraryDTO.CreateResponse cloneItinerary(UUID id, String token) {
        log.info("Cloning itinerary id={}", id);

        // Validate queue capacity before persisting
        QueueStatusDTO.Response queueStatus = queueManagementPort.getQueueStatus();
        if (queueStatus.totalQueueLength() >= queueManagementPort.getMaxQueueSize()) {
            throw new QueueFullException(
                "Processing queue is full (" + queueStatus.totalQueueLength() + " items). Please try again later.");
        }

        // Require authentication (get current user)
        UUID userId = authUseCase.getCurrentUserId()
            .orElseThrow(() -> new AuthenticationRequiredException("Authentication required to clone an itinerary"));

        log.info("Cloning itinerary id={} for userId={}", id, userId);

        // Validate access to the source itinerary (this will throw if access is denied)
        getById(id, token);

        // Fetch the full entity for cloning
        Itinerary sourceItinerary = persistencePort.findById(id)
            .orElseThrow(() -> new ItineraryNotFoundException("Itinerary not found: " + id));

        log.debug("Source itinerary userId={}, creating clone with userId={}",
                  sourceItinerary.getUserId(), userId);

        // Create a new itinerary with cloned data
        Itinerary cloned = Itinerary.create(
            sourceItinerary.getTitle() + " (Copy)",
            sourceItinerary.getDescription(),
            sourceItinerary.getTravelMode(),
            sourceItinerary.getPreferences(),
            userId  // Owned by the current user
        );

        // Clone all steps
        for (ItineraryStep sourceStep : sourceItinerary.getSteps()) {
            ItineraryStep clonedStep = ItineraryStep.create(
                sourceStep.getStepOrder(),
                sourceStep.getPlaceName(),
                sourceStep.getNotes(),
                sourceStep.getArrivalDate()
            );
            
            // Copy step-level preferences if present
            if (sourceStep.getPreferences() != null && !sourceStep.getPreferences().isBlank()) {
                clonedStep.setPreferences(sourceStep.getPreferences());
            }
            
            cloned.addStep(clonedStep);
        }

        // Persist the cloned itinerary
        Itinerary saved = persistencePort.save(cloned);

        log.info("Cloned itinerary persisted: id={}, userId={}, accessToken={}",
                 saved.getId(), saved.getUserId(), saved.getAccessToken());

        // Enqueue with priority based on user type
        AuthDTO.StatusResponse authStatus = authUseCase.getStatus();
        String userType = authStatus.userType();
        int queuePosition = queueManagementPort.enqueue(saved.getId(), userType);

        log.debug("Enqueued cloned itinerary id={} to {} queue at position={}",
                  saved.getId(), userType, queuePosition);

        saved.setQueuePosition(queuePosition);
        saved = persistencePort.save(saved);

        // Collect step place names for Saga Orchestrator worker dispatch
        List<String> stepPlaceNames = saved.getSteps().stream()
            .map(ItineraryStep::getPlaceName)
            .toList();

        // Publish domain event
        ItineraryCreatedEvent event = new ItineraryCreatedEvent(
            saved.getId(),
            saved.getAccessToken(),
            userType,
            saved.getTitle(),
            saved.getTravelMode() != null ? saved.getTravelMode().name() : null,
            saved.getSteps().size(),
            stepPlaceNames,
            saved.getPreferences(),
            UUID.randomUUID().toString(),  // correlationId
            Instant.now()
        );
        eventPublisher.publish(event);

        log.info("Itinerary cloned from id={} to new id={} queuePosition={}", 
                 id, saved.getId(), queuePosition);

        return new ItineraryDTO.CreateResponse(
            saved.getId(),
            saved.getAccessToken(),
            saved.getStatus().name(),
            queuePosition,
            saved.getEstimatedCompletion() != null ? saved.getEstimatedCompletion().toString() : null
        );
    }
}
