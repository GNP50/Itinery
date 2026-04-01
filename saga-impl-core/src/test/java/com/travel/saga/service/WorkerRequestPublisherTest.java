package com.travel.saga.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WorkerRequestPublisher.
 * Tests priority-based topic routing and event serialization.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WorkerRequestPublisher Tests")
class WorkerRequestPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private ObjectMapper objectMapper;
    private WorkerRequestPublisher publisher;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        publisher = new WorkerRequestPublisher(kafkaTemplate, objectMapper);
    }

    @Nested
    @DisplayName("Geo Processing Request Tests")
    class GeoProcessingTests {

        @Test
        @DisplayName("Should publish to admin topic for ADMIN user")
        void shouldPublishToAdminTopicForAdmin() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            String userType = "ADMIN";
            String preferencesJson = "{\"interests\":[\"museums\"]}";
            long sagaVersion = 1L;

            // When
            publisher.requestGeoProcessing(itineraryId, userType, preferencesJson, sagaVersion);

            // Then
            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

            verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

            assertThat(topicCaptor.getValue()).isEqualTo("geo.worker.request.admin");
            assertThat(keyCaptor.getValue()).isEqualTo(itineraryId.toString());

            WorkerRequestEvent event = objectMapper.readValue(payloadCaptor.getValue(), WorkerRequestEvent.class);
            assertThat(event.itineraryId()).isEqualTo(itineraryId);
            assertThat(event.workerType()).isEqualTo("GEO");
            assertThat(event.preferencesJson()).isEqualTo(preferencesJson);
            assertThat(event.sagaVersion()).isEqualTo(sagaVersion);
            assertThat(event.userType()).isEqualTo(userType);
        }

        @Test
        @DisplayName("Should publish to registered topic for REGISTERED user")
        void shouldPublishToRegisteredTopicForRegistered() {
            // Given
            UUID itineraryId = UUID.randomUUID();
            String userType = "REGISTERED";

            // When
            publisher.requestGeoProcessing(itineraryId, userType, "{}", 2L);

            // Then
            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(topicCaptor.capture(), anyString(), anyString());
            assertThat(topicCaptor.getValue()).isEqualTo("geo.worker.request.registered");
        }

        @Test
        @DisplayName("Should publish to anonymous topic for ANONYMOUS user")
        void shouldPublishToAnonymousTopicForAnonymous() {
            // Given
            UUID itineraryId = UUID.randomUUID();
            String userType = "ANONYMOUS";

            // When
            publisher.requestGeoProcessing(itineraryId, userType, "{}", 1L);

            // Then
            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(topicCaptor.capture(), anyString(), anyString());
            assertThat(topicCaptor.getValue()).isEqualTo("geo.worker.request.anonymous");
        }

        @Test
        @DisplayName("Should default to anonymous topic when userType is null")
        void shouldDefaultToAnonymousTopicWhenUserTypeNull() {
            // Given
            UUID itineraryId = UUID.randomUUID();

            // When
            publisher.requestGeoProcessing(itineraryId, null, "{}", 1L);

            // Then
            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(topicCaptor.capture(), anyString(), anyString());
            assertThat(topicCaptor.getValue()).isEqualTo("geo.worker.request.anonymous");
        }

        @Test
        @DisplayName("Should handle mixed case user type")
        void shouldHandleMixedCaseUserType() {
            // Given
            UUID itineraryId = UUID.randomUUID();
            String userType = "Admin"; // Mixed case

            // When
            publisher.requestGeoProcessing(itineraryId, userType, "{}", 1L);

            // Then
            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(topicCaptor.capture(), anyString(), anyString());
            assertThat(topicCaptor.getValue()).isEqualTo("geo.worker.request.admin");
        }
    }

    @Nested
    @DisplayName("Route Processing Request Tests")
    class RouteProcessingTests {

        @Test
        @DisplayName("Should publish to correct route topic for each user type")
        void shouldPublishToCorrectRouteTopicForEachUserType() {
            // Given
            UUID itineraryId = UUID.randomUUID();

            // When & Then - ADMIN
            publisher.requestRouteProcessing(itineraryId, "ADMIN", "{}", 1L);
            verify(kafkaTemplate).send(eq("route.worker.request.admin"), anyString(), anyString());

            // When & Then - REGISTERED
            publisher.requestRouteProcessing(itineraryId, "REGISTERED", "{}", 1L);
            verify(kafkaTemplate).send(eq("route.worker.request.registered"), anyString(), anyString());

            // When & Then - ANONYMOUS
            publisher.requestRouteProcessing(itineraryId, "ANONYMOUS", "{}", 1L);
            verify(kafkaTemplate).send(eq("route.worker.request.anonymous"), anyString(), anyString());
        }

        @Test
        @DisplayName("Should include sagaVersion in route request event")
        void shouldIncludeSagaVersionInRouteRequestEvent() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            long sagaVersion = 5L;

            // When
            publisher.requestRouteProcessing(itineraryId, "REGISTERED", "{\"route\":\"settings\"}", sagaVersion);

            // Then
            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(anyString(), anyString(), payloadCaptor.capture());

            WorkerRequestEvent event = objectMapper.readValue(payloadCaptor.getValue(), WorkerRequestEvent.class);
            assertThat(event.workerType()).isEqualTo("ROUTE");
            assertThat(event.sagaVersion()).isEqualTo(sagaVersion);
        }
    }

    @Nested
    @DisplayName("AI Processing Request Tests")
    class AiProcessingTests {

        @Test
        @DisplayName("Should publish to correct AI topic for each user type")
        void shouldPublishToCorrectAiTopicForEachUserType() {
            // Given
            UUID itineraryId = UUID.randomUUID();

            // When & Then - ADMIN
            publisher.requestAiProcessing(itineraryId, "ADMIN", "{}", 1L);
            verify(kafkaTemplate).send(eq("ai.worker.request.admin"), anyString(), anyString());

            // When & Then - REGISTERED
            publisher.requestAiProcessing(itineraryId, "REGISTERED", "{}", 1L);
            verify(kafkaTemplate).send(eq("ai.worker.request.registered"), anyString(), anyString());

            // When & Then - ANONYMOUS
            publisher.requestAiProcessing(itineraryId, "ANONYMOUS", "{}", 1L);
            verify(kafkaTemplate).send(eq("ai.worker.request.anonymous"), anyString(), anyString());
        }

        @Test
        @DisplayName("Should include preferences in AI request")
        void shouldIncludePreferencesInAiRequest() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            String preferencesJson = "{\"style\":\"casual\",\"generateAiTips\":true}";

            // When
            publisher.requestAiProcessing(itineraryId, "REGISTERED", preferencesJson, 2L);

            // Then
            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(anyString(), anyString(), payloadCaptor.capture());

            WorkerRequestEvent event = objectMapper.readValue(payloadCaptor.getValue(), WorkerRequestEvent.class);
            assertThat(event.workerType()).isEqualTo("AI");
            assertThat(event.preferencesJson()).isEqualTo(preferencesJson);
        }
    }

    @Nested
    @DisplayName("POI Processing Request Tests")
    class PoiProcessingTests {

        @Test
        @DisplayName("Should publish to correct POI topic for each user type")
        void shouldPublishToCorrectPoiTopicForEachUserType() {
            // Given
            UUID itineraryId = UUID.randomUUID();

            // When & Then - ADMIN
            publisher.requestPoiProcessing(itineraryId, "ADMIN", "{}", 1L);
            verify(kafkaTemplate).send(eq("poi.worker.request.admin"), anyString(), anyString());

            // When & Then - REGISTERED
            publisher.requestPoiProcessing(itineraryId, "REGISTERED", "{}", 1L);
            verify(kafkaTemplate).send(eq("poi.worker.request.registered"), anyString(), anyString());

            // When & Then - ANONYMOUS
            publisher.requestPoiProcessing(itineraryId, "ANONYMOUS", "{}", 1L);
            verify(kafkaTemplate).send(eq("poi.worker.request.anonymous"), anyString(), anyString());
        }

        @Test
        @DisplayName("Should serialize POI request correctly")
        void shouldSerializePoiRequestCorrectly() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            String userType = "ADMIN";
            String preferencesJson = "{\"interests\":[\"restaurants\",\"parks\"]}";
            long sagaVersion = 3L;

            // When
            publisher.requestPoiProcessing(itineraryId, userType, preferencesJson, sagaVersion);

            // Then
            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(anyString(), anyString(), payloadCaptor.capture());

            WorkerRequestEvent event = objectMapper.readValue(payloadCaptor.getValue(), WorkerRequestEvent.class);
            assertThat(event.itineraryId()).isEqualTo(itineraryId);
            assertThat(event.workerType()).isEqualTo("POI");
            assertThat(event.preferencesJson()).isEqualTo(preferencesJson);
            assertThat(event.sagaVersion()).isEqualTo(sagaVersion);
            assertThat(event.userType()).isEqualTo(userType);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle JSON serialization error gracefully")
        void shouldHandleJsonSerializationErrorGracefully() {
            // Given
            ObjectMapper failingMapper = mock(ObjectMapper.class);
            WorkerRequestPublisher failingPublisher = new WorkerRequestPublisher(kafkaTemplate, failingMapper);
            UUID itineraryId = UUID.randomUUID();

            try {
                when(failingMapper.writeValueAsString(any())).thenThrow(new RuntimeException("JSON error"));
            } catch (Exception e) {
                // Expected
            }

            // When
            failingPublisher.requestGeoProcessing(itineraryId, "ADMIN", "{}", 1L);

            // Then - Should not throw exception (logged internally)
            verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Should handle Kafka send error gracefully")
        void shouldHandleKafkaSendErrorGracefully() {
            // Given
            UUID itineraryId = UUID.randomUUID();
            when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Kafka unavailable"));

            // When
            publisher.requestGeoProcessing(itineraryId, "REGISTERED", "{}", 1L);

            // Then - Should not propagate exception (logged internally)
            verify(kafkaTemplate).send(anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should publish all worker types with correct topics and priorities")
        void shouldPublishAllWorkerTypesWithCorrectTopicsAndPriorities() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            String preferencesJson = "{\"test\":\"preferences\"}";
            long sagaVersion = 1L;

            // When
            publisher.requestGeoProcessing(itineraryId, "ADMIN", preferencesJson, sagaVersion);
            publisher.requestRouteProcessing(itineraryId, "REGISTERED", preferencesJson, sagaVersion);
            publisher.requestAiProcessing(itineraryId, "ANONYMOUS", preferencesJson, sagaVersion);
            publisher.requestPoiProcessing(itineraryId, "ADMIN", preferencesJson, sagaVersion);

            // Then
            verify(kafkaTemplate).send(eq("geo.worker.request.admin"), anyString(), anyString());
            verify(kafkaTemplate).send(eq("route.worker.request.registered"), anyString(), anyString());
            verify(kafkaTemplate).send(eq("ai.worker.request.anonymous"), anyString(), anyString());
            verify(kafkaTemplate).send(eq("poi.worker.request.admin"), anyString(), anyString());
            verify(kafkaTemplate, times(4)).send(anyString(), anyString(), anyString());
        }
    }
}
