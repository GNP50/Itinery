package com.travel.geo.impl.adapter.inbound.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.geo.api.port.inbound.PoiWorkerUseCase;
import com.travel.itinerary.api.kafka.WorkerCompleteEvent;
import com.travel.itinerary.api.kafka.WorkerFailedEvent;
import com.travel.itinerary.api.kafka.WorkerRequestEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PoiWorkerListener.
 * Tests POI discovery event consumption and processing.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PoiWorkerListener Tests")
class PoiWorkerListenerTest {

    @Mock
    private PoiWorkerUseCase poiWorkerUseCase;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private ObjectMapper objectMapper;
    private PoiWorkerListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        listener = new PoiWorkerListener(poiWorkerUseCase, kafkaTemplate, objectMapper);
    }

    @Nested
    @DisplayName("Successful Processing Tests")
    class SuccessfulProcessingTests {

        @Test
        @DisplayName("Should process POI request and publish completion event")
        void shouldProcessPoiRequestAndPublishCompletion() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "POI", "{}", 1L, "REGISTERED");
            String payload = objectMapper.writeValueAsString(requestEvent);

            doNothing().when(poiWorkerUseCase).processPoiDiscovery(itineraryId);

            // When
            listener.handlePoiRequest(payload);

            // Then
            verify(poiWorkerUseCase).processPoiDiscovery(itineraryId);

            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(topicCaptor.capture(), eventCaptor.capture());

            assertThat(topicCaptor.getValue()).isEqualTo("poi.completed");

            WorkerCompleteEvent completionEvent = objectMapper.readValue(eventCaptor.getValue(), WorkerCompleteEvent.class);
            assertThat(completionEvent.itineraryId()).isEqualTo(itineraryId);
            assertThat(completionEvent.workerType()).isEqualTo("POI");
        }

        @Test
        @DisplayName("Should handle request from admin priority topic")
        void shouldHandleRequestFromAdminPriorityTopic() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "POI", "{}", 1L, "ADMIN");
            String payload = objectMapper.writeValueAsString(requestEvent);

            // When
            listener.handlePoiRequest(payload);

            // Then
            verify(poiWorkerUseCase).processPoiDiscovery(itineraryId);
            verify(kafkaTemplate).send(eq("poi.completed"), anyString());
        }

        @Test
        @DisplayName("Should handle request from registered priority topic")
        void shouldHandleRequestFromRegisteredPriorityTopic() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "POI", "{}", 1L, "REGISTERED");
            String payload = objectMapper.writeValueAsString(requestEvent);

            // When
            listener.handlePoiRequest(payload);

            // Then
            verify(poiWorkerUseCase).processPoiDiscovery(itineraryId);
            verify(kafkaTemplate).send(eq("poi.completed"), anyString());
        }

        @Test
        @DisplayName("Should handle request from anonymous priority topic")
        void shouldHandleRequestFromAnonymousPriorityTopic() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "POI", "{}", 1L, "ANONYMOUS");
            String payload = objectMapper.writeValueAsString(requestEvent);

            // When
            listener.handlePoiRequest(payload);

            // Then
            verify(poiWorkerUseCase).processPoiDiscovery(itineraryId);
            verify(kafkaTemplate).send(eq("poi.completed"), anyString());
        }

        @Test
        @DisplayName("Should mark itinerary COMPLETED after POI discovery")
        void shouldMarkItineraryCompletedAfterPoiDiscovery() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "POI", "{}", 1L, "REGISTERED");
            String payload = objectMapper.writeValueAsString(requestEvent);

            // When
            listener.handlePoiRequest(payload);

            // Then
            verify(poiWorkerUseCase).processPoiDiscovery(itineraryId);
            verify(kafkaTemplate).send(eq("poi.completed"), anyString());
        }
    }

    @Nested
    @DisplayName("Failure Handling Tests")
    class FailureHandlingTests {

        @Test
        @DisplayName("Should publish failure event when processing fails")
        void shouldPublishFailureEventWhenProcessingFails() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            String errorMessage = "Overpass API timeout";

            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "POI", "{}", 1L, "REGISTERED");
            String payload = objectMapper.writeValueAsString(requestEvent);

            doThrow(new RuntimeException(errorMessage))
                .when(poiWorkerUseCase).processPoiDiscovery(any());

            // When
            listener.handlePoiRequest(payload);

            // Then
            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(topicCaptor.capture(), eventCaptor.capture());

            assertThat(topicCaptor.getValue()).isEqualTo("poi.failed");

            WorkerFailedEvent failureEvent = objectMapper.readValue(eventCaptor.getValue(), WorkerFailedEvent.class);
            assertThat(failureEvent.itineraryId()).isEqualTo(itineraryId);
            assertThat(failureEvent.workerType()).isEqualTo("POI");
            assertThat(failureEvent.errorMessage()).isEqualTo(errorMessage);
        }

        @Test
        @DisplayName("Should handle POI discovery exception with detailed error message")
        void shouldHandlePoiDiscoveryExceptionWithDetailedErrorMessage() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "POI", "{}", 1L, "ADMIN");
            String payload = objectMapper.writeValueAsString(requestEvent);

            doThrow(new IllegalStateException("No steps with coordinates found"))
                .when(poiWorkerUseCase).processPoiDiscovery(any());

            // When
            listener.handlePoiRequest(payload);

            // Then
            ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(eq("poi.failed"), eventCaptor.capture());

            WorkerFailedEvent failureEvent = objectMapper.readValue(eventCaptor.getValue(), WorkerFailedEvent.class);
            assertThat(failureEvent.errorMessage()).contains("No steps with coordinates found");
        }

        @Test
        @DisplayName("Should handle different exception types")
        void shouldHandleDifferentExceptionTypes() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "POI", "{}", 1L, "REGISTERED");
            String payload = objectMapper.writeValueAsString(requestEvent);

            doThrow(new NullPointerException("Itinerary not found"))
                .when(poiWorkerUseCase).processPoiDiscovery(any());

            // When
            listener.handlePoiRequest(payload);

            // Then
            ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(eq("poi.failed"), eventCaptor.capture());

            WorkerFailedEvent failureEvent = objectMapper.readValue(eventCaptor.getValue(), WorkerFailedEvent.class);
            assertThat(failureEvent.errorMessage()).contains("Itinerary not found");
        }
    }

    @Nested
    @DisplayName("Invalid Payload Handling Tests")
    class InvalidPayloadTests {

        @Test
        @DisplayName("Should skip processing when JSON parsing fails")
        void shouldSkipProcessingWhenJsonParsingFails() {
            // Given
            String invalidPayload = "{invalid json";

            // When
            listener.handlePoiRequest(invalidPayload);

            // Then
            verify(poiWorkerUseCase, never()).processPoiDiscovery(any());
            verify(kafkaTemplate, never()).send(anyString(), anyString());
        }

        @Test
        @DisplayName("Should skip processing when payload is empty")
        void shouldSkipProcessingWhenPayloadEmpty() {
            // Given
            String emptyPayload = "";

            // When
            listener.handlePoiRequest(emptyPayload);

            // Then
            verify(poiWorkerUseCase, never()).processPoiDiscovery(any());
            verify(kafkaTemplate, never()).send(anyString(), anyString());
        }

        @Test
        @DisplayName("Should skip processing when payload is null")
        void shouldSkipProcessingWhenPayloadNull() {
            // Given
            String nullPayload = null;

            // When
            listener.handlePoiRequest(nullPayload);

            // Then
            verify(poiWorkerUseCase, never()).processPoiDiscovery(any());
            verify(kafkaTemplate, never()).send(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Event Publishing Error Tests")
    class EventPublishingErrorTests {

        @Test
        @DisplayName("Should handle Kafka publish error for completion event")
        void shouldHandleKafkaPublishErrorForCompletionEvent() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "POI", "{}", 1L, "REGISTERED");
            String payload = objectMapper.writeValueAsString(requestEvent);

            doThrow(new RuntimeException("Kafka unavailable"))
                .when(kafkaTemplate).send(anyString(), anyString());

            // When
            listener.handlePoiRequest(payload);

            // Then
            verify(poiWorkerUseCase).processPoiDiscovery(itineraryId);
            verify(kafkaTemplate).send(eq("poi.completed"), anyString());
            // Should not propagate exception
        }

        @Test
        @DisplayName("Should handle Kafka publish error for failure event")
        void shouldHandleKafkaPublishErrorForFailureEvent() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "POI", "{}", 1L, "REGISTERED");
            String payload = objectMapper.writeValueAsString(requestEvent);

            doThrow(new RuntimeException("Processing error"))
                .when(poiWorkerUseCase).processPoiDiscovery(any());
            doThrow(new RuntimeException("Kafka unavailable"))
                .when(kafkaTemplate).send(anyString(), anyString());

            // When
            listener.handlePoiRequest(payload);

            // Then
            verify(kafkaTemplate).send(eq("poi.failed"), anyString());
            // Should not propagate exception
        }
    }

    @Nested
    @DisplayName("Multi-Topic Consumption Tests")
    class MultiTopicConsumptionTests {

        @Test
        @DisplayName("Should consume from all three priority topics")
        void shouldConsumeFromAllThreePriorityTopics() throws Exception {
            // Given
            UUID adminId = UUID.randomUUID();
            UUID registeredId = UUID.randomUUID();
            UUID anonymousId = UUID.randomUUID();

            WorkerRequestEvent adminEvent = new WorkerRequestEvent(adminId, "POI", "{}", 1L, "ADMIN");
            WorkerRequestEvent registeredEvent = new WorkerRequestEvent(registeredId, "POI", "{}", 1L, "REGISTERED");
            WorkerRequestEvent anonymousEvent = new WorkerRequestEvent(anonymousId, "POI", "{}", 1L, "ANONYMOUS");

            // When
            listener.handlePoiRequest(objectMapper.writeValueAsString(adminEvent));
            listener.handlePoiRequest(objectMapper.writeValueAsString(registeredEvent));
            listener.handlePoiRequest(objectMapper.writeValueAsString(anonymousEvent));

            // Then
            verify(poiWorkerUseCase).processPoiDiscovery(adminId);
            verify(poiWorkerUseCase).processPoiDiscovery(registeredId);
            verify(poiWorkerUseCase).processPoiDiscovery(anonymousId);
            verify(kafkaTemplate, times(3)).send(eq("poi.completed"), anyString());
        }
    }
}
