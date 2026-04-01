package com.travel.saga.service;

import com.travel.queue.port.QueueManagementPort;
import com.travel.saga.domain.SagaInstance;
import com.travel.saga.port.outbound.SagaPersistencePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SagaOrchestrationService.
 * Tests saga lifecycle, state transitions, queue management, retry logic, and idempotency.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SagaOrchestrationService Tests")
class SagaOrchestrationServiceTest {

    @Mock
    private SagaPersistencePort<SagaInstance> persistencePort;

    @Mock
    private WorkerRequestPublisher workerRequestPublisher;

    @Mock
    private SagaLifecyclePublisher sagaLifecyclePublisher;

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private QueueManagementPort queueManagementPort;

    private SagaOrchestrationService service;

    private static final int MAX_CONCURRENT = 5;

    @BeforeEach
    void setUp() {
        service = new SagaOrchestrationService(
            persistencePort,
            workerRequestPublisher,
            sagaLifecyclePublisher,
            taskScheduler,
            queueManagementPort
        );
        ReflectionTestUtils.setField(service, "maxConcurrent", MAX_CONCURRENT);
    }

    @Nested
    @DisplayName("Saga Start Tests")
    class SagaStartTests {

        @Test
        @DisplayName("Should start saga immediately when capacity available")
        void shouldStartImmediatelyWhenCapacityAvailable() {
            // Given
            UUID itineraryId = UUID.randomUUID();
            String userType = "REGISTERED";
            String preferencesJson = "{\"interests\":[\"museums\"]}";

            when(persistencePort.findByItineraryId(itineraryId)).thenReturn(Collections.emptyList());
            when(persistencePort.countByCurrentStateIn(anyList())).thenReturn(3L);

            // When
            service.start(itineraryId, userType, preferencesJson);

            // Then
            ArgumentCaptor<SagaInstance> sagaCaptor = ArgumentCaptor.forClass(SagaInstance.class);
            verify(persistencePort).save(sagaCaptor.capture());

            SagaInstance savedSaga = sagaCaptor.getValue();
            assertThat(savedSaga.getCurrentState()).isEqualTo("GEOCODING");
            assertThat(savedSaga.getItineraryId()).isEqualTo(itineraryId);
            assertThat(savedSaga.getUserType()).isEqualTo(userType);
            assertThat(savedSaga.getPreferences()).isEqualTo(preferencesJson);
            assertThat(savedSaga.getPriority()).isEqualTo(1); // REGISTERED priority
            assertThat(savedSaga.getSagaVersion()).isEqualTo(0);

            verify(workerRequestPublisher).requestGeoProcessing(itineraryId, userType, preferencesJson, 0L);
        }

        @Test
        @DisplayName("Should queue saga when capacity limit reached")
        void shouldQueueWhenCapacityLimitReached() {
            // Given
            UUID itineraryId = UUID.randomUUID();
            String userType = "ANONYMOUS";
            String preferencesJson = "{}";

            when(persistencePort.findByItineraryId(itineraryId)).thenReturn(Collections.emptyList());
            when(persistencePort.countByCurrentStateIn(anyList())).thenReturn(5L); // At capacity

            // When
            service.start(itineraryId, userType, preferencesJson);

            // Then
            ArgumentCaptor<SagaInstance> sagaCaptor = ArgumentCaptor.forClass(SagaInstance.class);
            verify(persistencePort).save(sagaCaptor.capture());

            SagaInstance savedSaga = sagaCaptor.getValue();
            assertThat(savedSaga.getCurrentState()).isEqualTo("QUEUED");
            assertThat(savedSaga.getPriority()).isEqualTo(0); // ANONYMOUS priority

            verify(workerRequestPublisher, never()).requestGeoProcessing(any(), any(), any(), anyLong());
        }

        @Test
        @DisplayName("Should assign correct priority for ADMIN user")
        void shouldAssignAdminPriority() {
            // Given
            UUID itineraryId = UUID.randomUUID();
            String userType = "ADMIN";

            when(persistencePort.findByItineraryId(itineraryId)).thenReturn(Collections.emptyList());
            when(persistencePort.countByCurrentStateIn(anyList())).thenReturn(3L);

            // When
            service.start(itineraryId, userType, "{}");

            // Then
            ArgumentCaptor<SagaInstance> sagaCaptor = ArgumentCaptor.forClass(SagaInstance.class);
            verify(persistencePort).save(sagaCaptor.capture());
            assertThat(sagaCaptor.getValue().getPriority()).isEqualTo(2); // ADMIN priority
        }

        @Test
        @DisplayName("Should skip if saga already exists (idempotent)")
        void shouldSkipIfSagaAlreadyExists() {
            // Given
            UUID itineraryId = UUID.randomUUID();
            SagaInstance existingSaga = SagaInstance.create(itineraryId, "REGISTERED", "{}");

            when(persistencePort.findByItineraryId(itineraryId)).thenReturn(List.of(existingSaga));

            // When
            service.start(itineraryId, "REGISTERED", "{}");

            // Then
            verify(persistencePort, never()).save(any());
            verify(workerRequestPublisher, never()).requestGeoProcessing(any(), any(), any(), anyLong());
        }
    }

    @Nested
    @DisplayName("Saga Restart Tests")
    class SagaRestartTests {

        @Test
        @DisplayName("Should restart existing saga with incremented version")
        void shouldRestartExistingSagaWithIncrementedVersion() {
            // Given
            UUID itineraryId = UUID.randomUUID();
            SagaInstance existingSaga = SagaInstance.create(itineraryId, "REGISTERED", "{\"old\":\"prefs\"}");
            existingSaga.updateState("COMPLETED");
            existingSaga.addCompletedStep("GEOCODING");
            existingSaga.setFailedStep("AI");
            existingSaga.setErrorMessage("Test error");
            ReflectionTestUtils.setField(existingSaga, "sagaVersion", 1L);

            when(persistencePort.findByItineraryId(itineraryId)).thenReturn(List.of(existingSaga));
            when(persistencePort.countByCurrentStateIn(anyList())).thenReturn(3L);

            // When
            service.restart(itineraryId, "ADMIN", "{\"new\":\"prefs\"}");

            // Then
            verify(persistencePort).save(existingSaga);
            assertThat(existingSaga.getCurrentState()).isEqualTo("GEOCODING");
            assertThat(existingSaga.getUserType()).isEqualTo("ADMIN");
            assertThat(existingSaga.getPreferences()).isEqualTo("{\"new\":\"prefs\"}");
            assertThat(existingSaga.getPriority()).isEqualTo(2); // ADMIN priority
            assertThat(existingSaga.getSagaVersion()).isEqualTo(2L); // Incremented
            assertThat(existingSaga.getFailedStep()).isNull();
            assertThat(existingSaga.getErrorMessage()).isNull();
            assertThat(existingSaga.getCompletedSteps()).isEmpty();
            assertThat(existingSaga.getRetryCount()).isEqualTo(0);

            verify(workerRequestPublisher).requestGeoProcessing(itineraryId, "ADMIN", "{\"new\":\"prefs\"}", 2L);
        }

        @Test
        @DisplayName("Should create new saga if none exists on restart")
        void shouldCreateNewSagaIfNoneExistsOnRestart() {
            // Given
            UUID itineraryId = UUID.randomUUID();
            when(persistencePort.findByItineraryId(itineraryId)).thenReturn(Collections.emptyList());
            when(persistencePort.countByCurrentStateIn(anyList())).thenReturn(3L);

            // When
            service.restart(itineraryId, "REGISTERED", "{}");

            // Then
            ArgumentCaptor<SagaInstance> sagaCaptor = ArgumentCaptor.forClass(SagaInstance.class);
            verify(persistencePort).save(sagaCaptor.capture());
            assertThat(sagaCaptor.getValue().getCurrentState()).isEqualTo("GEOCODING");
        }

        @Test
        @DisplayName("Should queue restarted saga when capacity limit reached")
        void shouldQueueRestartedSagaWhenCapacityReached() {
            // Given
            UUID itineraryId = UUID.randomUUID();
            SagaInstance existingSaga = SagaInstance.create(itineraryId, "REGISTERED", "{}");

            when(persistencePort.findByItineraryId(itineraryId)).thenReturn(List.of(existingSaga));
            when(persistencePort.countByCurrentStateIn(anyList())).thenReturn(5L); // At capacity

            // When
            service.restart(itineraryId, "REGISTERED", "{}");

            // Then
            assertThat(existingSaga.getCurrentState()).isEqualTo("QUEUED");
        }
    }

    @Nested
    @DisplayName("Process Pending Sagas Tests")
    class ProcessPendingSagasTests {

        @Test
        @DisplayName("Should process queued sagas when slots available")
        void shouldProcessQueuedSagasWhenSlotsAvailable() {
            // Given
            when(persistencePort.countByCurrentStateIn(anyList())).thenReturn(2L); // 3 slots available

            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            SagaInstance saga1 = SagaInstance.create(id1, "ADMIN", "{}");
            saga1.updateState("QUEUED");
            SagaInstance saga2 = SagaInstance.create(id2, "REGISTERED", "{}");
            saga2.updateState("QUEUED");

            when(persistencePort.findQueuedByPriority(3)).thenReturn(List.of(saga1, saga2));
            when(persistencePort.findByItineraryId(id1)).thenReturn(List.of(saga1));
            when(persistencePort.findByItineraryId(id2)).thenReturn(List.of(saga2));

            // When
            service.processPendingSagas();

            // Then
            verify(persistencePort).save(saga1);
            verify(persistencePort).save(saga2);
            assertThat(saga1.getCurrentState()).isEqualTo("GEOCODING");
            assertThat(saga2.getCurrentState()).isEqualTo("GEOCODING");

            verify(workerRequestPublisher).requestGeoProcessing(eq(id1), anyString(), anyString(), anyLong());
            verify(workerRequestPublisher).requestGeoProcessing(eq(id2), anyString(), anyString(), anyLong());
        }

        @Test
        @DisplayName("Should skip processing when no slots available")
        void shouldSkipProcessingWhenNoSlotsAvailable() {
            // Given
            when(persistencePort.countByCurrentStateIn(anyList())).thenReturn(5L); // At capacity

            // When
            service.processPendingSagas();

            // Then
            verify(persistencePort, never()).findQueuedByPriority(anyInt());
            verify(workerRequestPublisher, never()).requestGeoProcessing(any(), any(), any(), anyLong());
        }

        @Test
        @DisplayName("Should remove orphaned queue items")
        void shouldRemoveOrphanedQueueItems() {
            // Given
            when(persistencePort.countByCurrentStateIn(anyList())).thenReturn(2L);

            UUID orphanedId = UUID.randomUUID();
            SagaInstance orphanedSaga = SagaInstance.create(orphanedId, "REGISTERED", "{}");
            orphanedSaga.updateState("QUEUED");

            when(persistencePort.findQueuedByPriority(3)).thenReturn(List.of(orphanedSaga));
            when(persistencePort.findByItineraryId(orphanedId)).thenReturn(Collections.emptyList()); // Not found in DB

            // When
            service.processPendingSagas();

            // Then
            verify(queueManagementPort).remove(orphanedId);
            verify(persistencePort, never()).save(orphanedSaga);
            verify(workerRequestPublisher, never()).requestGeoProcessing(any(), any(), any(), anyLong());
        }

        @Test
        @DisplayName("Should skip processing when no queued sagas")
        void shouldSkipWhenNoQueuedSagas() {
            // Given
            when(persistencePort.countByCurrentStateIn(anyList())).thenReturn(2L);
            when(persistencePort.findQueuedByPriority(3)).thenReturn(Collections.emptyList());

            // When
            service.processPendingSagas();

            // Then
            verify(workerRequestPublisher, never()).requestGeoProcessing(any(), any(), any(), anyLong());
        }
    }

    @Nested
    @DisplayName("Completion Handler Tests")
    class CompletionHandlerTests {

        @Test
        @DisplayName("Should handle geo completion and request route processing")
        void shouldHandleGeoCompletionAndRequestRoute() {
            // Given
            UUID itineraryId = UUID.randomUUID();
            SagaInstance saga = SagaInstance.create(itineraryId, "REGISTERED", "{}");
            saga.updateState("GEOCODING");
            ReflectionTestUtils.setField(saga, "sagaVersion", 1L);

            when(persistencePort.findByItineraryId(itineraryId)).thenReturn(List.of(saga));

            // When
            service.onGeoCompleted(itineraryId, 1L);

            // Then
            verify(persistencePort).save(saga);
            assertThat(saga.getCurrentState()).isEqualTo("ROUTING");
            assertThat(saga.getCompletedSteps()).contains("GEOCODING");
            assertThat(saga.getFailedStep()).isNull();
            assertThat(saga.getErrorMessage()).isNull();

            verify(workerRequestPublisher).requestRouteProcessing(itineraryId, "REGISTERED", "{}", 1L);
        }

        @Test
        @DisplayName("Should discard stale geo completion event")
        void shouldDiscardStaleGeoCompletionEvent() {
            // Given
            UUID itineraryId = UUID.randomUUID();
            SagaInstance saga = SagaInstance.create(itineraryId, "REGISTERED", "{}");
            ReflectionTestUtils.setField(saga, "sagaVersion", 3L);

            when(persistencePort.findByItineraryId(itineraryId)).thenReturn(List.of(saga));

            // When
            service.onGeoCompleted(itineraryId, 1L); // Old version

            // Then
            verify(persistencePort, never()).save(any());
            verify(workerRequestPublisher, never()).requestRouteProcessing(any(), any(), any(), anyLong());
        }

        @Test
        @DisplayName("Should handle route completion and request AI processing")
        void shouldHandleRouteCompletionAndRequestAi() {
            // Given
            UUID itineraryId = UUID.randomUUID();
            SagaInstance saga = SagaInstance.create(itineraryId, "ADMIN", "{}");
            saga.updateState("ROUTING");
            ReflectionTestUtils.setField(saga, "sagaVersion", 2L);

            when(persistencePort.findByItineraryId(itineraryId)).thenReturn(List.of(saga));

            // When
            service.onRouteCompleted(itineraryId, 2L);

            // Then
            assertThat(saga.getCurrentState()).isEqualTo("AI_ENRICHMENT");
            assertThat(saga.getCompletedSteps()).contains("ROUTING");
            verify(workerRequestPublisher).requestAiProcessing(itineraryId, "ADMIN", "{}", 2L);
        }

        @Test
        @DisplayName("Should handle AI completion and request POI discovery")
        void shouldHandleAiCompletionAndRequestPoi() {
            // Given
            UUID itineraryId = UUID.randomUUID();
            SagaInstance saga = SagaInstance.create(itineraryId, "REGISTERED", "{}");
            saga.updateState("AI_ENRICHMENT");
            ReflectionTestUtils.setField(saga, "sagaVersion", 1L);

            when(persistencePort.findByItineraryId(itineraryId)).thenReturn(List.of(saga));

            // When
            service.onAiCompleted(itineraryId, 1L);

            // Then
            assertThat(saga.getCurrentState()).isEqualTo("POI_DISCOVERY");
            assertThat(saga.getCompletedSteps()).contains("AI_ENRICHMENT");
            verify(workerRequestPublisher).requestPoiProcessing(itineraryId, "REGISTERED", "{}", 1L);
        }

        @Test
        @DisplayName("Should handle POI completion and mark saga completed")
        void shouldHandlePoiCompletionAndMarkCompleted() {
            // Given
            UUID itineraryId = UUID.randomUUID();
            SagaInstance saga = SagaInstance.create(itineraryId, "ANONYMOUS", "{}");
            saga.updateState("POI_DISCOVERY");
            ReflectionTestUtils.setField(saga, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(saga, "sagaVersion", 1L);

            when(persistencePort.findByItineraryId(itineraryId)).thenReturn(List.of(saga));

            // When
            service.onPoiCompleted(itineraryId, 1L);

            // Then
            assertThat(saga.getCurrentState()).isEqualTo("COMPLETED");
            assertThat(saga.getCompletedSteps()).contains("POI_DISCOVERY");
            verify(sagaLifecyclePublisher).publishSagaCompleted(eq(itineraryId), any(UUID.class));
        }

        @Test
        @DisplayName("Should throw exception when saga not found")
        void shouldThrowExceptionWhenSagaNotFound() {
            // Given
            UUID itineraryId = UUID.randomUUID();
            when(persistencePort.findByItineraryId(itineraryId)).thenReturn(Collections.emptyList());

            // When & Then
            assertThatThrownBy(() -> service.onGeoCompleted(itineraryId, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No SagaInstance found");
        }
    }

    @Nested
    @DisplayName("Failure Handler Tests")
    class FailureHandlerTests {

        @Test
        @DisplayName("Should retry geo failure with exponential backoff")
        void shouldRetryGeoFailureWithExponentialBackoff() {
            // Given
            UUID itineraryId = UUID.randomUUID();
            SagaInstance saga = SagaInstance.create(itineraryId, "REGISTERED", "{}");
            saga.updateState("GEOCODING");
            saga.setMaxRetries(3);
            ReflectionTestUtils.setField(saga, "sagaVersion", 1L);

            when(persistencePort.findByItineraryId(itineraryId)).thenReturn(List.of(saga));

            // When
            service.onGeoFailed(itineraryId, "Network timeout", 1L);

            // Then
            verify(persistencePort).save(saga);
            assertThat(saga.getFailedStep()).isEqualTo("GEO");
            assertThat(saga.getErrorMessage()).isEqualTo("Network timeout");
            assertThat(saga.getRetryCount()).isEqualTo(1);

            verify(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
            verify(sagaLifecyclePublisher, never()).publishSagaFailed(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should compensate after max retries exceeded")
        void shouldCompensateAfterMaxRetriesExceeded() {
            // Given
            UUID itineraryId = UUID.randomUUID();
            SagaInstance saga = SagaInstance.create(itineraryId, "ANONYMOUS", "{}");
            saga.updateState("ROUTING");
            saga.setMaxRetries(3);
            saga.setRetryCount(3); // Already at max
            UUID sagaId = UUID.randomUUID();
            ReflectionTestUtils.setField(saga, "id", sagaId);
            ReflectionTestUtils.setField(saga, "sagaVersion", 1L);

            when(persistencePort.findByItineraryId(itineraryId)).thenReturn(List.of(saga));

            // When
            service.onRouteFailed(itineraryId, "Service unavailable", 1L);

            // Then
            verify(persistencePort).save(saga);
            assertThat(saga.getCurrentState()).isEqualTo("FAILED");
            assertThat(saga.getFailedStep()).isEqualTo("ROUTE");

            verify(sagaLifecyclePublisher).publishSagaFailed(itineraryId, sagaId, "ROUTE", "Service unavailable");
            verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
        }

        @Test
        @DisplayName("Should discard stale failure event")
        void shouldDiscardStaleFailureEvent() {
            // Given
            UUID itineraryId = UUID.randomUUID();
            SagaInstance saga = SagaInstance.create(itineraryId, "REGISTERED", "{}");
            ReflectionTestUtils.setField(saga, "sagaVersion", 3L);

            when(persistencePort.findByItineraryId(itineraryId)).thenReturn(List.of(saga));

            // When
            service.onAiFailed(itineraryId, "Error", 1L); // Old version

            // Then
            verify(persistencePort, never()).save(any());
            verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
        }

        @Test
        @DisplayName("Should calculate exponential backoff correctly")
        void shouldCalculateExponentialBackoffCorrectly() {
            // Given
            UUID itineraryId = UUID.randomUUID();
            SagaInstance saga = SagaInstance.create(itineraryId, "REGISTERED", "{}");
            saga.updateState("AI_ENRICHMENT");
            saga.setMaxRetries(5);
            ReflectionTestUtils.setField(saga, "sagaVersion", 1L);

            when(persistencePort.findByItineraryId(itineraryId)).thenReturn(List.of(saga));

            // When - First retry (2s)
            service.onAiFailed(itineraryId, "Error 1", 1L);
            assertThat(saga.getRetryCount()).isEqualTo(1);

            // When - Second retry (4s)
            service.onAiFailed(itineraryId, "Error 2", 1L);
            assertThat(saga.getRetryCount()).isEqualTo(2);

            // When - Third retry (8s)
            service.onAiFailed(itineraryId, "Error 3", 1L);
            assertThat(saga.getRetryCount()).isEqualTo(3);

            // Then
            verify(taskScheduler, times(3)).schedule(any(Runnable.class), any(Instant.class));
        }
    }

    @Nested
    @DisplayName("Compensation Tests")
    class CompensationTests {

        @Test
        @DisplayName("Should compensate saga and publish failure event")
        void shouldCompensateSagaAndPublishFailure() {
            // Given
            UUID itineraryId = UUID.randomUUID();
            UUID sagaId = UUID.randomUUID();
            SagaInstance saga = SagaInstance.create(itineraryId, "REGISTERED", "{}");
            ReflectionTestUtils.setField(saga, "id", sagaId);

            when(persistencePort.findByItineraryId(itineraryId)).thenReturn(List.of(saga));

            // When
            service.compensate(itineraryId, "POI", "External service timeout");

            // Then
            verify(persistencePort).save(saga);
            assertThat(saga.getCurrentState()).isEqualTo("FAILED");
            assertThat(saga.getFailedStep()).isEqualTo("POI");
            assertThat(saga.getErrorMessage()).isEqualTo("External service timeout");

            verify(sagaLifecyclePublisher).publishSagaFailed(itineraryId, sagaId, "POI", "External service timeout");
        }

        @Test
        @DisplayName("Should handle compensate by saga ID")
        void shouldHandleCompensateBySagaId() {
            // Given
            UUID sagaId = UUID.randomUUID();
            UUID itineraryId = UUID.randomUUID();
            SagaInstance saga = SagaInstance.create(itineraryId, "ADMIN", "{}");
            saga.setFailedStep("ROUTING");
            ReflectionTestUtils.setField(saga, "id", sagaId);

            when(persistencePort.findById(sagaId)).thenReturn(Optional.of(saga));
            when(persistencePort.findByItineraryId(itineraryId)).thenReturn(List.of(saga));

            // When
            service.compensate(sagaId);

            // Then
            verify(sagaLifecyclePublisher).publishSagaFailed(eq(itineraryId), eq(sagaId), eq("ROUTING"), anyString());
        }
    }

    @Nested
    @DisplayName("Retry Saga Tests")
    class RetrySagaTests {

        @Test
        @DisplayName("Should retry saga in GEOCODING state")
        void shouldRetrySagaInGeocodingState() {
            // Given
            UUID itineraryId = UUID.randomUUID();
            SagaInstance saga = SagaInstance.create(itineraryId, "REGISTERED", "{\"retry\":true}");
            saga.updateState("GEOCODING");
            saga.setRetryCount(2);
            ReflectionTestUtils.setField(saga, "sagaVersion", 1L);

            // When
            service.retrySaga(saga);

            // Then
            verify(persistencePort).save(saga);
            verify(workerRequestPublisher).requestGeoProcessing(itineraryId, "REGISTERED", "{\"retry\":true}", 1L);
        }

        @Test
        @DisplayName("Should retry saga in ROUTING state")
        void shouldRetrySagaInRoutingState() {
            // Given
            UUID itineraryId = UUID.randomUUID();
            SagaInstance saga = SagaInstance.create(itineraryId, "ADMIN", "{}");
            saga.updateState("ROUTING");
            ReflectionTestUtils.setField(saga, "sagaVersion", 2L);

            // When
            service.retrySaga(saga);

            // Then
            verify(workerRequestPublisher).requestRouteProcessing(itineraryId, "ADMIN", "{}", 2L);
        }

        @Test
        @DisplayName("Should retry saga in AI_ENRICHMENT state")
        void shouldRetrySagaInAiEnrichmentState() {
            // Given
            UUID itineraryId = UUID.randomUUID();
            SagaInstance saga = SagaInstance.create(itineraryId, "REGISTERED", "{}");
            saga.updateState("AI_ENRICHMENT");
            ReflectionTestUtils.setField(saga, "sagaVersion", 1L);

            // When
            service.retrySaga(saga);

            // Then
            verify(workerRequestPublisher).requestAiProcessing(itineraryId, "REGISTERED", "{}", 1L);
        }

        @Test
        @DisplayName("Should retry saga in POI_DISCOVERY state")
        void shouldRetrySagaInPoiDiscoveryState() {
            // Given
            UUID itineraryId = UUID.randomUUID();
            SagaInstance saga = SagaInstance.create(itineraryId, "ANONYMOUS", "{}");
            saga.updateState("POI_DISCOVERY");
            ReflectionTestUtils.setField(saga, "sagaVersion", 1L);

            // When
            service.retrySaga(saga);

            // Then
            verify(workerRequestPublisher).requestPoiProcessing(itineraryId, "ANONYMOUS", "{}", 1L);
        }
    }
}
