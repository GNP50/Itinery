package com.travel.geo.impl.adapter.inbound.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.geo.api.port.inbound.RouteWorkerUseCase;
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
 * Unit tests for RouteWorkerListener.
 * Tests route calculation event consumption and processing.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RouteWorkerListener Tests")
class RouteWorkerListenerTest {

    @Mock
    private RouteWorkerUseCase routeWorkerUseCase;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private ObjectMapper objectMapper;
    private RouteWorkerListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        listener = new RouteWorkerListener(routeWorkerUseCase, kafkaTemplate, objectMapper);
    }

    @Nested
    @DisplayName("Successful Processing Tests")
    class SuccessfulProcessingTests {

        @Test
        @DisplayName("Should process route request and publish completion event")
        void shouldProcessRouteRequestAndPublishCompletion() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "ROUTE", "{}", 1L, "REGISTERED");
            String payload = objectMapper.writeValueAsString(requestEvent);

            doNothing().when(routeWorkerUseCase).processRouting(itineraryId);

            // When
            listener.handleRouteRequest(payload);

            // Then
            verify(routeWorkerUseCase).processRouting(itineraryId);

            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(topicCaptor.capture(), eventCaptor.capture());

            assertThat(topicCaptor.getValue()).isEqualTo("route.completed");

            WorkerCompleteEvent completionEvent = objectMapper.readValue(eventCaptor.getValue(), WorkerCompleteEvent.class);
            assertThat(completionEvent.itineraryId()).isEqualTo(itineraryId);
            assertThat(completionEvent.workerType()).isEqualTo("ROUTE");
        }

        @Test
        @DisplayName("Should handle request from admin priority topic")
        void shouldHandleRequestFromAdminPriorityTopic() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "ROUTE", "{}", 1L, "ADMIN");
            String payload = objectMapper.writeValueAsString(requestEvent);

            // When
            listener.handleRouteRequest(payload);

            // Then
            verify(routeWorkerUseCase).processRouting(itineraryId);
            verify(kafkaTemplate).send(eq("route.completed"), anyString());
        }

        @Test
        @DisplayName("Should handle request from registered priority topic")
        void shouldHandleRequestFromRegisteredPriorityTopic() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "ROUTE", "{}", 1L, "REGISTERED");
            String payload = objectMapper.writeValueAsString(requestEvent);

            // When
            listener.handleRouteRequest(payload);

            // Then
            verify(routeWorkerUseCase).processRouting(itineraryId);
            verify(kafkaTemplate).send(eq("route.completed"), anyString());
        }

        @Test
        @DisplayName("Should handle request from anonymous priority topic")
        void shouldHandleRequestFromAnonymousPriorityTopic() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "ROUTE", "{}", 1L, "ANONYMOUS");
            String payload = objectMapper.writeValueAsString(requestEvent);

            // When
            listener.handleRouteRequest(payload);

            // Then
            verify(routeWorkerUseCase).processRouting(itineraryId);
            verify(kafkaTemplate).send(eq("route.completed"), anyString());
        }

        @Test
        @DisplayName("Should calculate routes between consecutive steps")
        void shouldCalculateRoutesBetweenConsecutiveSteps() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "ROUTE", "{}", 1L, "REGISTERED");
            String payload = objectMapper.writeValueAsString(requestEvent);

            // When
            listener.handleRouteRequest(payload);

            // Then
            verify(routeWorkerUseCase).processRouting(itineraryId);
            verify(kafkaTemplate).send(eq("route.completed"), anyString());
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
            String errorMessage = "OSRM service unavailable";

            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "ROUTE", "{}", 1L, "REGISTERED");
            String payload = objectMapper.writeValueAsString(requestEvent);

            doThrow(new RuntimeException(errorMessage))
                .when(routeWorkerUseCase).processRouting(any());

            // When
            listener.handleRouteRequest(payload);

            // Then
            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(topicCaptor.capture(), eventCaptor.capture());

            assertThat(topicCaptor.getValue()).isEqualTo("route.failed");

            WorkerFailedEvent failureEvent = objectMapper.readValue(eventCaptor.getValue(), WorkerFailedEvent.class);
            assertThat(failureEvent.itineraryId()).isEqualTo(itineraryId);
            assertThat(failureEvent.workerType()).isEqualTo("ROUTE");
            assertThat(failureEvent.errorMessage()).isEqualTo(errorMessage);
        }

        @Test
        @DisplayName("Should handle routing exception with detailed error message")
        void shouldHandleRoutingExceptionWithDetailedErrorMessage() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "ROUTE", "{}", 1L, "ADMIN");
            String payload = objectMapper.writeValueAsString(requestEvent);

            doThrow(new IllegalStateException("No valid route found between coordinates"))
                .when(routeWorkerUseCase).processRouting(any());

            // When
            listener.handleRouteRequest(payload);

            // Then
            ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(eq("route.failed"), eventCaptor.capture());

            WorkerFailedEvent failureEvent = objectMapper.readValue(eventCaptor.getValue(), WorkerFailedEvent.class);
            assertThat(failureEvent.errorMessage()).contains("No valid route found between coordinates");
        }

        @Test
        @DisplayName("Should handle different exception types")
        void shouldHandleDifferentExceptionTypes() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "ROUTE", "{}", 1L, "REGISTERED");
            String payload = objectMapper.writeValueAsString(requestEvent);

            doThrow(new NullPointerException("Missing coordinates for step"))
                .when(routeWorkerUseCase).processRouting(any());

            // When
            listener.handleRouteRequest(payload);

            // Then
            ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(eq("route.failed"), eventCaptor.capture());

            WorkerFailedEvent failureEvent = objectMapper.readValue(eventCaptor.getValue(), WorkerFailedEvent.class);
            assertThat(failureEvent.errorMessage()).contains("Missing coordinates for step");
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
            listener.handleRouteRequest(invalidPayload);

            // Then
            verify(routeWorkerUseCase, never()).processRouting(any());
            verify(kafkaTemplate, never()).send(anyString(), anyString());
        }

        @Test
        @DisplayName("Should skip processing when payload is empty")
        void shouldSkipProcessingWhenPayloadEmpty() {
            // Given
            String emptyPayload = "";

            // When
            listener.handleRouteRequest(emptyPayload);

            // Then
            verify(routeWorkerUseCase, never()).processRouting(any());
            verify(kafkaTemplate, never()).send(anyString(), anyString());
        }

        @Test
        @DisplayName("Should skip processing when payload is null")
        void shouldSkipProcessingWhenPayloadNull() {
            // Given
            String nullPayload = null;

            // When
            listener.handleRouteRequest(nullPayload);

            // Then
            verify(routeWorkerUseCase, never()).processRouting(any());
            verify(kafkaTemplate, never()).send(anyString(), anyString());
        }

        @Test
        @DisplayName("Should skip processing when payload is malformed")
        void shouldSkipProcessingWhenPayloadMalformed() {
            // Given
            String malformedPayload = "{\"itineraryId\":\"not-a-uuid\"}";

            // When
            listener.handleRouteRequest(malformedPayload);

            // Then
            verify(routeWorkerUseCase, never()).processRouting(any());
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
            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "ROUTE", "{}", 1L, "REGISTERED");
            String payload = objectMapper.writeValueAsString(requestEvent);

            doThrow(new RuntimeException("Kafka unavailable"))
                .when(kafkaTemplate).send(anyString(), anyString());

            // When
            listener.handleRouteRequest(payload);

            // Then
            verify(routeWorkerUseCase).processRouting(itineraryId);
            verify(kafkaTemplate).send(eq("route.completed"), anyString());
            // Should not propagate exception
        }

        @Test
        @DisplayName("Should handle Kafka publish error for failure event")
        void shouldHandleKafkaPublishErrorForFailureEvent() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "ROUTE", "{}", 1L, "REGISTERED");
            String payload = objectMapper.writeValueAsString(requestEvent);

            doThrow(new RuntimeException("Processing error"))
                .when(routeWorkerUseCase).processRouting(any());
            doThrow(new RuntimeException("Kafka unavailable"))
                .when(kafkaTemplate).send(anyString(), anyString());

            // When
            listener.handleRouteRequest(payload);

            // Then
            verify(kafkaTemplate).send(eq("route.failed"), anyString());
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

            WorkerRequestEvent adminEvent = new WorkerRequestEvent(adminId, "ROUTE", "{}", 1L, "ADMIN");
            WorkerRequestEvent registeredEvent = new WorkerRequestEvent(registeredId, "ROUTE", "{}", 1L, "REGISTERED");
            WorkerRequestEvent anonymousEvent = new WorkerRequestEvent(anonymousId, "ROUTE", "{}", 1L, "ANONYMOUS");

            // When
            listener.handleRouteRequest(objectMapper.writeValueAsString(adminEvent));
            listener.handleRouteRequest(objectMapper.writeValueAsString(registeredEvent));
            listener.handleRouteRequest(objectMapper.writeValueAsString(anonymousEvent));

            // Then
            verify(routeWorkerUseCase).processRouting(adminId);
            verify(routeWorkerUseCase).processRouting(registeredId);
            verify(routeWorkerUseCase).processRouting(anonymousId);
            verify(kafkaTemplate, times(3)).send(eq("route.completed"), anyString());
        }
    }

    @Nested
    @DisplayName("Integration Scenario Tests")
    class IntegrationScenarioTests {

        @Test
        @DisplayName("Should handle multiple consecutive route requests")
        void shouldHandleMultipleConsecutiveRouteRequests() throws Exception {
            // Given
            UUID itinerary1 = UUID.randomUUID();
            UUID itinerary2 = UUID.randomUUID();
            UUID itinerary3 = UUID.randomUUID();

            WorkerRequestEvent request1 = new WorkerRequestEvent(itinerary1, "ROUTE", "{}", 1L, "ADMIN");
            WorkerRequestEvent request2 = new WorkerRequestEvent(itinerary2, "ROUTE", "{}", 1L, "REGISTERED");
            WorkerRequestEvent request3 = new WorkerRequestEvent(itinerary3, "ROUTE", "{}", 1L, "ANONYMOUS");

            // When
            listener.handleRouteRequest(objectMapper.writeValueAsString(request1));
            listener.handleRouteRequest(objectMapper.writeValueAsString(request2));
            listener.handleRouteRequest(objectMapper.writeValueAsString(request3));

            // Then
            verify(routeWorkerUseCase, times(3)).processRouting(any());
            verify(kafkaTemplate, times(3)).send(eq("route.completed"), anyString());
        }
    }
}
