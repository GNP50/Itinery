package com.travel.saga.service;

import com.travel.queue.port.QueueManagementPort;
import com.travel.saga.domain.SagaInstance;
import com.travel.saga.port.outbound.SagaPersistencePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
 import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Stateless orchestration service that drives the itinerary processing saga
 * using {@link SagaInstance} rows in the database as the durable state store.
 *
 * <h3>Flow</h3>
 * <pre>
 * itinerary.created
 *   → start()        → SagaInstance(INITIAL)  → geo.worker.request
 * geo.completed
 *   → onGeoCompleted → SagaInstance(GEOCODING) → route.worker.request
 * route.completed
 *   → onRouteCompleted → SagaInstance(ROUTING)  → ai.worker.request
 * ai.completed
 *   → onAiCompleted  → SagaInstance(AI_ENRICHMENT) → poi.worker.request
 * poi.completed
 *   → onPoiCompleted → SagaInstance(COMPLETED)
 * *_failed (after maxRetries)
 *   → compensate()  → SagaInstance(FAILED) + itinerary.status=FAILED
 * </pre>
 *
 * <p>Retry uses exponential back-off scheduled via {@link TaskScheduler}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SagaOrchestrationService {

    private static final long RETRY_BASE_DELAY_MS = 2_000L;
    private static final long RETRY_MAX_DELAY_MS  = 30_000L;

    /**
     * List of states considered "active processing".
     * These states count towards the maxConcurrent limit.
     */
    private static final List<String> ACTIVE_STATES = List.of(
        "GEOCODING", "ROUTING", "AI_ENRICHMENT", "POI_DISCOVERY"
    );

    private final SagaPersistencePort<SagaInstance> persistencePort;
    private final WorkerRequestPublisher            workerRequestPublisher;
    private final SagaLifecyclePublisher            sagaLifecyclePublisher;
    private final TaskScheduler                     taskScheduler;
    private final QueueManagementPort               queueManagementPort;

    @Value("${saga.max-concurrent:5}")
    private int maxConcurrent;

    // -------------------------------------------------------------------------
    // Saga start
    // -------------------------------------------------------------------------

    /**
     * Creates a new {@link SagaInstance} and either starts it immediately or queues it.
     * <p>
     * If the number of active sagas exceeds {@code maxConcurrent}, the saga is created
     * with state {@code QUEUED} and will be picked up by the {@link #processPendingSagas()}
     * scheduler when capacity becomes available.
     * <p>
     * Idempotent: if a saga already exists for the itinerary it is reused.
     *
     * @param itineraryId    UUID of the itinerary to process
     * @param userType       user type (REGISTERED, ADMIN, ANONYMOUS) for priority calculation
     * @param preferencesJson JSON blob with user preferences
     */
    @Transactional
    public void start(UUID itineraryId, String userType, String preferencesJson) {
        List<SagaInstance> existing = persistencePort.findByItineraryId(itineraryId);
        if (!existing.isEmpty()) {
            log.warn("SagaOrchestration: saga already exists for itinerary id={} – skipping", itineraryId);
            return;
        }

        // Check if we have capacity to start immediately
        long activeSagas = persistencePort.countByCurrentStateIn(ACTIVE_STATES);
        
        SagaInstance saga = SagaInstance.create(itineraryId, userType, preferencesJson);
        
        if (activeSagas >= maxConcurrent) {
            // Queue the saga for later processing
            saga.updateState("QUEUED");
            persistencePort.save(saga);
            
            log.info("SagaOrchestration: saga {} for itinerary id={} QUEUED (active={}, max={}, priority={}, userType={})",
                     saga.getId(), itineraryId, activeSagas, maxConcurrent, saga.getPriority(), userType);
        } else {
            // Start immediately
            saga.updateState("GEOCODING");
            persistencePort.save(saga);

            log.info("SagaOrchestration: started saga {} for itinerary id={} immediately (priority={}, userType={})",
                     saga.getId(), itineraryId, saga.getPriority(), userType);
            workerRequestPublisher.requestGeoProcessing(itineraryId, saga.getUserType(), preferencesJson, saga.getSagaVersion());
        }
    }

    /**
     * Restarts an existing saga for an updated itinerary.
     * <p>
     * This method is called when an itinerary is updated (PUT operation).
     * It resets the existing saga to the initial state and re-queues or restarts
     * the processing workflow based on current capacity.
     * <p>
     * If no saga exists for the itinerary, a new one is created (fallback to start()).
     *
     * @param itineraryId    UUID of the itinerary to restart
     * @param userType       user type (REGISTERED, ADMIN, ANONYMOUS) for priority calculation
     * @param preferencesJson updated JSON blob with user preferences
     */
    @Transactional
    public void restart(UUID itineraryId, String userType, String preferencesJson) {
        List<SagaInstance> existing = persistencePort.findByItineraryId(itineraryId);
        
        if (existing.isEmpty()) {
            log.info("SagaOrchestration: no existing saga for itinerary id={} – creating new one", itineraryId);
            start(itineraryId, userType, preferencesJson);
            return;
        }

        SagaInstance saga = existing.get(0);
        
        // Reset saga state for re-processing
        saga.updateState("INITIAL");
        saga.setFailedStep(null);
        saga.setErrorMessage(null);
        saga.getCompletedSteps().clear();
        saga.setRetryCount(0);
        saga.setPreferences(preferencesJson);
        saga.setUserType(userType);
        saga.recalculatePriority();
        saga.incrementSagaVersion(); // Increment version to invalidate old worker events
        
        log.info("SagaOrchestration: restarting saga {} for updated itinerary id={} (userType={}, priority={}, newVersion={})",
                 saga.getId(), itineraryId, userType, saga.getPriority(), saga.getSagaVersion());

        // Check if we have capacity to start immediately
        long activeSagas = persistencePort.countByCurrentStateIn(ACTIVE_STATES);
        
        if (activeSagas >= maxConcurrent) {
            // Queue the saga for later processing
            saga.updateState("QUEUED");
            persistencePort.save(saga);
            
            log.info("SagaOrchestration: saga {} for itinerary id={} re-QUEUED (active={}, max={}, priority={})",
                     saga.getId(), itineraryId, activeSagas, maxConcurrent, saga.getPriority());
        } else {
            // Start immediately
            saga.updateState("GEOCODING");
            persistencePort.save(saga);

            log.info("SagaOrchestration: saga {} for itinerary id={} restarted immediately (priority={})",
                     saga.getId(), itineraryId, saga.getPriority());
            workerRequestPublisher.requestGeoProcessing(itineraryId, saga.getUserType(), preferencesJson, saga.getSagaVersion());
        }
    }

    /**
     * Scheduled method that processes pending sagas from the QUEUED state.
     * <p>
     * Runs every 5 seconds and checks if there are available processing slots.
     * If slots are available, it picks the highest-priority queued sagas and starts them.
     * <p>
     * <b>Cleanup Logic:</b> When processing, if a saga doesn't exist in DB, it's removed
     * from Redis queue and skipped (prevents orphaned IDs from blocking the queue).
     */
    @Scheduled(fixedDelayString = "${saga.poll-interval-ms:5000}")
    @Transactional
    public void processPendingSagas() {
        long activeSagas = persistencePort.countByCurrentStateIn(ACTIVE_STATES);
        int availableSlots = maxConcurrent - (int) activeSagas;

        if (availableSlots <= 0) {
            log.trace("SagaOrchestration: no available slots (active={}, max={})", activeSagas, maxConcurrent);
            return;
        }

        // Fetch queued sagas ordered by priority (DESC) and creation time (ASC)
        List<SagaInstance> queuedSagas = persistencePort.findQueuedByPriority(availableSlots);

        if (queuedSagas.isEmpty()) {
            log.trace("SagaOrchestration: no pending sagas in QUEUED state");
            return;
        }

        log.info("SagaOrchestration: processing {} pending sagas (available slots={})",
                 queuedSagas.size(), availableSlots);

        for (SagaInstance saga : queuedSagas) {
            // CLEANUP: Verify saga still exists (handles orphaned Redis queue items)
            // If saga was deleted or doesn't exist, remove from queue and skip
            List<SagaInstance> verification = persistencePort.findByItineraryId(saga.getItineraryId());
            if (verification.isEmpty()) {
                log.warn("SagaOrchestration: saga for itinerary id={} not found in DB (orphaned queue item) - removing from queue",
                         saga.getItineraryId());
                queueManagementPort.remove(saga.getItineraryId());
                continue; // Skip to next saga
            }

            saga.updateState("GEOCODING");
            persistencePort.save(saga);

            log.info("SagaOrchestration: starting queued saga {} for itinerary id={} (priority={}, userType={})",
                     saga.getId(), saga.getItineraryId(), saga.getPriority(), saga.getUserType());

            workerRequestPublisher.requestGeoProcessing(saga.getItineraryId(), saga.getUserType(), saga.getPreferences(), saga.getSagaVersion());
        }
    }

    // -------------------------------------------------------------------------
    // Completion handlers
    // -------------------------------------------------------------------------

    @Transactional
    public void onGeoCompleted(UUID itineraryId, Long eventSagaVersion) {
        SagaInstance saga = requireSaga(itineraryId);
        
        // Idempotency check: discard events from previous saga versions
        if (eventSagaVersion != null && eventSagaVersion < saga.getSagaVersion()) {
            log.warn("SagaOrchestration: discarding stale geo.completed event for itinerary id={} " +
                     "(event version={}, current version={})", 
                     itineraryId, eventSagaVersion, saga.getSagaVersion());
            return;
        }
        
        saga.addCompletedStep("GEOCODING");
        saga.updateState("ROUTING");
        saga.setFailedStep(null);
        saga.setErrorMessage(null);
        persistencePort.save(saga);

        log.info("SagaOrchestration: GEO completed for itinerary id={} (version={})",
                 itineraryId, saga.getSagaVersion());
        workerRequestPublisher.requestRouteProcessing(itineraryId, saga.getUserType(), saga.getPreferences(), saga.getSagaVersion());
    }

    @Transactional
    public void onRouteCompleted(UUID itineraryId, Long eventSagaVersion) {
        SagaInstance saga = requireSaga(itineraryId);
        
        // Idempotency check: discard events from previous saga versions
        if (eventSagaVersion != null && eventSagaVersion < saga.getSagaVersion()) {
            log.warn("SagaOrchestration: discarding stale route.completed event for itinerary id={} " +
                     "(event version={}, current version={})", 
                     itineraryId, eventSagaVersion, saga.getSagaVersion());
            return;
        }
        
        saga.addCompletedStep("ROUTING");
        saga.updateState("AI_ENRICHMENT");
        saga.setFailedStep(null);
        saga.setErrorMessage(null);
        persistencePort.save(saga);

        log.info("SagaOrchestration: ROUTE completed for itinerary id={} (version={})",
                 itineraryId, saga.getSagaVersion());
        workerRequestPublisher.requestAiProcessing(itineraryId, saga.getUserType(), saga.getPreferences(), saga.getSagaVersion());
    }

    @Transactional
    public void onAiCompleted(UUID itineraryId, Long eventSagaVersion) {
        SagaInstance saga = requireSaga(itineraryId);
        
        // Idempotency check: discard events from previous saga versions
        if (eventSagaVersion != null && eventSagaVersion < saga.getSagaVersion()) {
            log.warn("SagaOrchestration: discarding stale ai.completed event for itinerary id={} " +
                     "(event version={}, current version={})", 
                     itineraryId, eventSagaVersion, saga.getSagaVersion());
            return;
        }
        
        saga.addCompletedStep("AI_ENRICHMENT");
        saga.updateState("POI_DISCOVERY");
        saga.setFailedStep(null);
        saga.setErrorMessage(null);
        persistencePort.save(saga);

        log.info("SagaOrchestration: AI completed for itinerary id={} (version={})",
                 itineraryId, saga.getSagaVersion());
        workerRequestPublisher.requestPoiProcessing(itineraryId, saga.getUserType(), saga.getPreferences(), saga.getSagaVersion());
    }

    @Transactional
    public void onPoiCompleted(UUID itineraryId, Long eventSagaVersion) {
        SagaInstance saga = requireSaga(itineraryId);
        
        // Idempotency check: discard events from previous saga versions
        if (eventSagaVersion != null && eventSagaVersion < saga.getSagaVersion()) {
            log.warn("SagaOrchestration: discarding stale poi.completed event for itinerary id={} " +
                     "(event version={}, current version={})", 
                     itineraryId, eventSagaVersion, saga.getSagaVersion());
            return;
        }
        
        saga.addCompletedStep("POI_DISCOVERY");
        saga.updateState("COMPLETED");
        saga.setFailedStep(null);
        saga.setErrorMessage(null);
        persistencePort.save(saga);

        // Publish saga completion event to be consumed by itinerary domain
        sagaLifecyclePublisher.publishSagaCompleted(itineraryId, saga.getId());

        log.info("SagaOrchestration: POI completed – saga COMPLETED for itinerary id={} (version={})", 
                 itineraryId, saga.getSagaVersion());
    }

    // -------------------------------------------------------------------------
    // Failure handlers with retry / compensation
    // -------------------------------------------------------------------------

    @Transactional
    public void onGeoFailed(UUID itineraryId, String errorMessage, Long eventSagaVersion) {
        SagaInstance saga = requireSaga(itineraryId);
        
        // Idempotency check: discard events from previous saga versions
        if (eventSagaVersion != null && eventSagaVersion < saga.getSagaVersion()) {
            log.warn("SagaOrchestration: discarding stale geo.failed event for itinerary id={} " +
                     "(event version={}, current version={})", 
                     itineraryId, eventSagaVersion, saga.getSagaVersion());
            return;
        }
        
        handleFailure(itineraryId, "GEO", errorMessage,
                      () -> workerRequestPublisher.requestGeoProcessing(itineraryId, saga.getUserType(), saga.getPreferences(), saga.getSagaVersion()));
    }

    @Transactional
    public void onRouteFailed(UUID itineraryId, String errorMessage, Long eventSagaVersion) {
        SagaInstance saga = requireSaga(itineraryId);
        
        // Idempotency check: discard events from previous saga versions
        if (eventSagaVersion != null && eventSagaVersion < saga.getSagaVersion()) {
            log.warn("SagaOrchestration: discarding stale route.failed event for itinerary id={} " +
                     "(event version={}, current version={})", 
                     itineraryId, eventSagaVersion, saga.getSagaVersion());
            return;
        }
        
        handleFailure(itineraryId, "ROUTE", errorMessage,
                      () -> workerRequestPublisher.requestRouteProcessing(itineraryId, saga.getUserType(), saga.getPreferences(), saga.getSagaVersion()));
    }

    @Transactional
    public void onAiFailed(UUID itineraryId, String errorMessage, Long eventSagaVersion) {
        SagaInstance saga = requireSaga(itineraryId);
        
        // Idempotency check: discard events from previous saga versions
        if (eventSagaVersion != null && eventSagaVersion < saga.getSagaVersion()) {
            log.warn("SagaOrchestration: discarding stale ai.failed event for itinerary id={} " +
                     "(event version={}, current version={})", 
                     itineraryId, eventSagaVersion, saga.getSagaVersion());
            return;
        }
        
        handleFailure(itineraryId, "AI", errorMessage,
                      () -> workerRequestPublisher.requestAiProcessing(itineraryId, saga.getUserType(), saga.getPreferences(), saga.getSagaVersion()));
    }

    @Transactional
    public void onPoiFailed(UUID itineraryId, String errorMessage, Long eventSagaVersion) {
        SagaInstance saga = requireSaga(itineraryId);
        
        // Idempotency check: discard events from previous saga versions
        if (eventSagaVersion != null && eventSagaVersion < saga.getSagaVersion()) {
            log.warn("SagaOrchestration: discarding stale poi.failed event for itinerary id={} " +
                     "(event version={}, current version={})", 
                     itineraryId, eventSagaVersion, saga.getSagaVersion());
            return;
        }
        
        handleFailure(itineraryId, "POI", errorMessage,
                      () -> workerRequestPublisher.requestPoiProcessing(itineraryId, saga.getUserType(), saga.getPreferences(), saga.getSagaVersion()));
    }

    // -------------------------------------------------------------------------
    // Compensation
    // -------------------------------------------------------------------------

    /**
     * Marks the saga as {@code FAILED} and publishes a saga failure event
     * to be consumed by the itinerary domain using proper port/adapter pattern.
     */
    @Transactional
    public void compensate(UUID itineraryId, String failedStep, String reason) {
        List<SagaInstance> sagas = persistencePort.findByItineraryId(itineraryId);
        if (!sagas.isEmpty()) {
            SagaInstance saga = sagas.get(0);
            saga.updateState("FAILED");
            saga.setFailedStep(failedStep);
            saga.setErrorMessage(reason);
            persistencePort.save(saga);

            // Publish saga failure event to be consumed by itinerary domain
            sagaLifecyclePublisher.publishSagaFailed(itineraryId, saga.getId(), failedStep, reason);

            log.warn("SagaOrchestration: saga FAILED for itinerary id={} (step={}, reason={})",
                     itineraryId, failedStep, reason);
        }
    }

    /**
     * Overloaded compensate method for SagaCommandPort integration.
     * Retrieves saga by ID and triggers compensation.
     */
    @Transactional
    public void compensate(UUID sagaId) {
        var sagaOpt = persistencePort.findById(sagaId);
        if (sagaOpt.isPresent()) {
            SagaInstance saga = sagaOpt.get();
            compensate(saga.getItineraryId(),
                      saga.getFailedStep() != null ? saga.getFailedStep() : "MANUAL_COMPENSATION",
                      "Manual compensation triggered");
        }
    }

    /**
     * Retry a saga instance. Resets retry count and re-dispatches the failed step.
     */
    @Transactional
    public void retrySaga(SagaInstance saga) {
        saga.resetForRetry();
        persistencePort.save(saga);

        // Re-dispatch based on current state
        switch (saga.getCurrentState()) {
            case "GEOCODING", "INITIAL" ->
                workerRequestPublisher.requestGeoProcessing(saga.getItineraryId(), saga.getUserType(), saga.getPreferences(), saga.getSagaVersion());
            case "ROUTING" ->
                workerRequestPublisher.requestRouteProcessing(saga.getItineraryId(), saga.getUserType(), saga.getPreferences(), saga.getSagaVersion());
            case "AI_ENRICHMENT" ->
                workerRequestPublisher.requestAiProcessing(saga.getItineraryId(), saga.getUserType(), saga.getPreferences(), saga.getSagaVersion());
            case "POI_DISCOVERY" ->
                workerRequestPublisher.requestPoiProcessing(saga.getItineraryId(), saga.getUserType(), saga.getPreferences(), saga.getSagaVersion());
            default ->
                log.warn("Cannot retry saga in state: {}", saga.getCurrentState());
        }

        log.info("SagaOrchestration: retry initiated for saga {} in state {}",
                 saga.getId(), saga.getCurrentState());
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void handleFailure(UUID itineraryId, String step, String errorMessage, Runnable retryAction) {
        SagaInstance saga = requireSaga(itineraryId);
        saga.setFailedStep(step);
        saga.setErrorMessage(errorMessage);

        if (saga.canRetry()) {
            saga.incrementRetryCount();
            long delayMs = Math.min(
                (long) (RETRY_BASE_DELAY_MS * Math.pow(2, saga.getRetryCount() - 1)),
                RETRY_MAX_DELAY_MS
            );
            persistencePort.save(saga);

            log.warn("SagaOrchestration: {} failed for itinerary id={} (attempt {}/{}) – retrying in {}ms",
                     step, itineraryId, saga.getRetryCount(), saga.getMaxRetries(), delayMs);

            taskScheduler.schedule(retryAction, Instant.now().plusMillis(delayMs));
        } else {
            log.error("SagaOrchestration: {} failed after {} retries for itinerary id={} – compensating",
                      step, saga.getMaxRetries(), itineraryId);
            // Update saga state to FAILED in-transaction (avoids self-call proxy bypass)
            saga.updateState("FAILED");
            persistencePort.save(saga);

            // Publish saga failure event to be consumed by itinerary domain
            sagaLifecyclePublisher.publishSagaFailed(itineraryId, saga.getId(), step, errorMessage);

            log.warn("SagaOrchestration: saga FAILED for itinerary id={} (step={}, reason={})",
                     itineraryId, step, errorMessage);
        }
    }

    private SagaInstance requireSaga(UUID itineraryId) {
        List<SagaInstance> sagas = persistencePort.findByItineraryId(itineraryId);
        if (sagas.isEmpty()) {
            throw new IllegalStateException("No SagaInstance found for itinerary id=" + itineraryId);
        }
        return sagas.get(0);
    }

    private String getPreferences(UUID itineraryId) {
        List<SagaInstance> sagas = persistencePort.findByItineraryId(itineraryId);
        return sagas.isEmpty() ? null : sagas.get(0).getPreferences();
    }
}
