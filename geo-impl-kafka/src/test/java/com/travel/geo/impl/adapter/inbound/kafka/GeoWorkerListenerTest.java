package com.travel.geo.impl.adapter.inbound.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.geo.api.port.inbound.GeoWorkerUseCase;
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
 * Unit tests for GeoWorkerListener.
 * Tests geocoding event consumption and processing.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GeoWorkerListener Tests")
class GeoWorkerListenerTest {

    @Mock
    private GeoWorkerUseCase geoWorkerUseCase;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private ObjectMapper objectMapper;
    private GeoWorkerListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        listener = new GeoWorkerListener(geoWorkerUseCase, kafkaTemplate, objectMapper);
    }

    @Nested
    @DisplayName("Successful Processing Tests")
    class SuccessfulProcessingTests {

        @Test
        @DisplayName("Should process geo request and publish completion event with sagaVersion")
        void shouldProcessGeoRequestAndPublishCompletionWithSagaVersion() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            long sagaVersion = 2L;

            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "GEO", "{}", sagaVersion, "REGISTERED");
            String payload = objectMapper.writeValueAsString(requestEvent);

            doNothing().when(geoWorkerUseCase).processGeocoding(itineraryId);

            // When
            listener.handleGeoRequest(payload);

            // Then
            verify(geoWorkerUseCase).processGeocoding(itineraryId);

            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(topicCaptor.capture(), eventCaptor.capture());

            assertThat(topicCaptor.getValue()).isEqualTo("geo.completed");

            WorkerCompleteEvent completionEvent = objectMapper.readValue(eventCaptor.getValue(), WorkerCompleteEvent.class);
            assertThat(completionEvent.itineraryId()).isEqualTo(itineraryId);
            assertThat(completionEvent.workerType()).isEqualTo("GEO");
            assertThat(completionEvent.sagaVersion()).isEqualTo(sagaVersion);
        }

        @Test
        @DisplayName("Should handle request from admin priority topic")
        void shouldHandleRequestFromAdminPriorityTopic() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "GEO", "{}", 1L, "ADMIN");
            String payload = objectMapper.writeValueAsString(requestEvent);

            // When
            listener.handleGeoRequest(payload);

            // Then
            verify(geoWorkerUseCase).processGeocoding(itineraryId);
            verify(kafkaTemplate).send(eq("geo.completed"), anyString());
        }

        @Test
        @DisplayName("Should handle request from registered priority topic")
        void shouldHandleRequestFromRegisteredPriorityTopic() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "GEO", "{}", 1L, "REGISTERED");
            String payload = objectMapper.writeValueAsString(requestEvent);

            // When
            listener.handleGeoRequest(payload);

            // Then
            verify(geoWorkerUseCase).processGeocoding(itineraryId);
            verify(kafkaTemplate).send(eq("geo.completed"), anyString());
        }

        @Test
        @DisplayName("Should handle request from anonymous priority topic")
        void shouldHandleRequestFromAnonymousPriorityTopic() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "GEO", "{}", 1L, "ANONYMOUS");
            String payload = objectMapper.writeValueAsString(requestEvent);

            // When
            listener.handleGeoRequest(payload);

            // Then
            verify(geoWorkerUseCase).processGeocoding(itineraryId);
            verify(kafkaTemplate).send(eq("geo.completed"), anyString());
        }

        @Test
        @DisplayName("Should propagate sagaVersion from request to completion event")
        void shouldPropagateSagaVersionFromRequestToCompletion() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            long sagaVersion = 7L;
            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "GEO", "{}", sagaVersion, "ADMIN");
            String payload = objectMapper.writeValueAsString(requestEvent);

            // When
            listener.handleGeoRequest(payload);

            // Then
            ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(eq("geo.completed"), eventCaptor.capture());

            WorkerCompleteEvent completionEvent = objectMapper.readValue(eventCaptor.getValue(), WorkerCompleteEvent.class);
            assertThat(completionEvent.sagaVersion()).isEqualTo(sagaVersion);
        }
    }

    @Nested
    @DisplayName("Failure Handling Tests")
    class FailureHandlingTests {

        @Test
        @DisplayName("Should publish failure event with sagaVersion when processing fails")
        void shouldPublishFailureEventWithSagaVersionWhenProcessingFails() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            String errorMessage = "Nominatim service unavailable";
            long sagaVersion = 3L;

            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "GEO", "{}", sagaVersion, "REGISTERED");
            String payload = objectMapper.writeValueAsString(requestEvent);

            doThrow(new RuntimeException(errorMessage))
                .when(geoWorkerUseCase).processGeocoding(any());

            // When
            listener.handleGeoRequest(payload);

            // Then
            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(topicCaptor.capture(), eventCaptor.capture());

            assertThat(topicCaptor.getValue()).isEqualTo("geo.failed");

            WorkerFailedEvent failureEvent = objectMapper.readValue(eventCaptor.getValue(), WorkerFailedEvent.class);
            assertThat(failureEvent.itineraryId()).isEqualTo(itineraryId);
            assertThat(failureEvent.workerType()).isEqualTo("GEO");
            assertThat(failureEvent.errorMessage()).isEqualTo(errorMessage);
            assertThat(failureEvent.sagaVersion()).isEqualTo(sagaVersion);
        }

        @Test
        @DisplayName("Should propagate sagaVersion in failure event")
        void shouldPropagateSagaVersionInFailureEvent() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            long sagaVersion = 5L;
            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "GEO", "{}", sagaVersion, "ADMIN");
            String payload = objectMapper.writeValueAsString(requestEvent);

            doThrow(new RuntimeException("Test error"))
                .when(geoWorkerUseCase).processGeocoding(any());

            // When
            listener.handleGeoRequest(payload);

            // Then
            ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(eq("geo.failed"), eventCaptor.capture());

            WorkerFailedEvent failureEvent = objectMapper.readValue(eventCaptor.getValue(), WorkerFailedEvent.class);
            assertThat(failureEvent.sagaVersion()).isEqualTo(sagaVersion);
        }

        @Test
        @DisplayName("Should handle geocoding exception with detailed error message")
        void shouldHandleGeocodingExceptionWithDetailedErrorMessage() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "GEO", "{}", 1L, "REGISTERED");
            String payload = objectMapper.writeValueAsString(requestEvent);

            doThrow(new IllegalArgumentException("Invalid address format"))
                .when(geoWorkerUseCase).processGeocoding(any());

            // When
            listener.handleGeoRequest(payload);

            // Then
            ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(eq("geo.failed"), eventCaptor.capture());

            WorkerFailedEvent failureEvent = objectMapper.readValue(eventCaptor.getValue(), WorkerFailedEvent.class);
            assertThat(failureEvent.errorMessage()).contains("Invalid address format");
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
            listener.handleGeoRequest(invalidPayload);

            // Then
            verify(geoWorkerUseCase, never()).processGeocoding(any());
            verify(kafkaTemplate, never()).send(anyString(), anyString());
        }

        @Test
        @DisplayName("Should skip processing when payload is null")
        void shouldSkipProcessingWhenPayloadNull() {
            // Given
            String nullPayload = null;

            // When
            listener.handleGeoRequest(nullPayload);

            // Then
            verify(geoWorkerUseCase, never()).processGeocoding(any());
            verify(kafkaTemplate, never()).send(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Backward Compatibility Tests")
    class BackwardCompatibilityTests {

        @Test
        @DisplayName("Should handle request event without sagaVersion")
        void shouldHandleRequestEventWithoutSagaVersion() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            WorkerRequestEvent oldFormatEvent = new WorkerRequestEvent(itineraryId, "GEO", "{}");
            String payload = objectMapper.writeValueAsString(oldFormatEvent);

            // When
            listener.handleGeoRequest(payload);

            // Then
            verify(geoWorkerUseCase).processGeocoding(itineraryId);

            ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(eq("geo.completed"), eventCaptor.capture());

            WorkerCompleteEvent completionEvent = objectMapper.readValue(eventCaptor.getValue(), WorkerCompleteEvent.class);
            assertThat(completionEvent.sagaVersion()).isEqualTo(1L); // Default value
        }
    }
}
