package com.travel.ai.impl.adapter.inbound.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.ai.api.port.inbound.AiWorkerUseCase;
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
 * Unit tests for AiWorkerListener.
 * Tests event consumption, processing, and response publication.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AiWorkerListener Tests")
class AiWorkerListenerTest {

    @Mock
    private AiWorkerUseCase aiWorkerUseCase;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private ObjectMapper objectMapper;
    private AiWorkerListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        listener = new AiWorkerListener(aiWorkerUseCase, kafkaTemplate, objectMapper);
    }

    @Nested
    @DisplayName("Successful Processing Tests")
    class SuccessfulProcessingTests {

        @Test
        @DisplayName("Should process AI request and publish completion event")
        void shouldProcessAiRequestAndPublishCompletion() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            String preferencesJson = "{\"style\":\"casual\",\"generateAiTips\":true}";
            long sagaVersion = 1L;

            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "AI", preferencesJson, sagaVersion, "REGISTERED");
            String payload = objectMapper.writeValueAsString(requestEvent);

            doNothing().when(aiWorkerUseCase).processAiEnrichment(itineraryId, preferencesJson);

            // When
            listener.handleAiRequest(payload);

            // Then
            verify(aiWorkerUseCase).processAiEnrichment(itineraryId, preferencesJson);

            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(topicCaptor.capture(), eventCaptor.capture());

            assertThat(topicCaptor.getValue()).isEqualTo("ai.completed");

            WorkerCompleteEvent completionEvent = objectMapper.readValue(eventCaptor.getValue(), WorkerCompleteEvent.class);
            assertThat(completionEvent.itineraryId()).isEqualTo(itineraryId);
            assertThat(completionEvent.workerType()).isEqualTo("AI");
            // sagaVersion will be 1L (default) since AiWorkerListener doesn't propagate it yet
        }

        @Test
        @DisplayName("Should handle request from admin priority topic")
        void shouldHandleRequestFromAdminPriorityTopic() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "AI", "{}", 2L, "ADMIN");
            String payload = objectMapper.writeValueAsString(requestEvent);

            // When
            listener.handleAiRequest(payload);

            // Then
            verify(aiWorkerUseCase).processAiEnrichment(itineraryId, "{}");
            verify(kafkaTemplate).send(eq("ai.completed"), anyString());
        }

        @Test
        @DisplayName("Should handle request from registered priority topic")
        void shouldHandleRequestFromRegisteredPriorityTopic() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "AI", "{}", 1L, "REGISTERED");
            String payload = objectMapper.writeValueAsString(requestEvent);

            // When
            listener.handleAiRequest(payload);

            // Then
            verify(aiWorkerUseCase).processAiEnrichment(itineraryId, "{}");
            verify(kafkaTemplate).send(eq("ai.completed"), anyString());
        }

        @Test
        @DisplayName("Should handle request from anonymous priority topic")
        void shouldHandleRequestFromAnonymousPriorityTopic() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "AI", "{}", 1L, "ANONYMOUS");
            String payload = objectMapper.writeValueAsString(requestEvent);

            // When
            listener.handleAiRequest(payload);

            // Then
            verify(aiWorkerUseCase).processAiEnrichment(itineraryId, "{}");
            verify(kafkaTemplate).send(eq("ai.completed"), anyString());
        }

        @Test
        @DisplayName("Should propagate preferences JSON to use case")
        void shouldPropagatePreferencesJsonToUseCase() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            String preferencesJson = "{\"interests\":[\"history\",\"art\"],\"generateAiTips\":true,\"style\":\"formal\"}";
            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "AI", preferencesJson, 1L, "REGISTERED");
            String payload = objectMapper.writeValueAsString(requestEvent);

            // When
            listener.handleAiRequest(payload);

            // Then
            verify(aiWorkerUseCase).processAiEnrichment(itineraryId, preferencesJson);
        }

        @Test
        @DisplayName("Should publish completion event with default sagaVersion")
        void shouldPublishCompletionEventWithDefaultSagaVersion() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            long sagaVersion = 5L;
            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "AI", "{}", sagaVersion, "ADMIN");
            String payload = objectMapper.writeValueAsString(requestEvent);

            // When
            listener.handleAiRequest(payload);

            // Then
            ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(eq("ai.completed"), eventCaptor.capture());

            WorkerCompleteEvent completionEvent = objectMapper.readValue(eventCaptor.getValue(), WorkerCompleteEvent.class);
            // NOTE: AiWorkerListener currently doesn't propagate sagaVersion (uses old 2-param constructor)
            assertThat(completionEvent.sagaVersion()).isEqualTo(1L); // Default value
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
            String errorMessage = "Ollama service unavailable";
            long sagaVersion = 1L;

            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "AI", "{}", sagaVersion, "REGISTERED");
            String payload = objectMapper.writeValueAsString(requestEvent);

            doThrow(new RuntimeException(errorMessage))
                .when(aiWorkerUseCase).processAiEnrichment(any(), any());

            // When
            listener.handleAiRequest(payload);

            // Then
            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(topicCaptor.capture(), eventCaptor.capture());

            assertThat(topicCaptor.getValue()).isEqualTo("ai.failed");

            WorkerFailedEvent failureEvent = objectMapper.readValue(eventCaptor.getValue(), WorkerFailedEvent.class);
            assertThat(failureEvent.itineraryId()).isEqualTo(itineraryId);
            assertThat(failureEvent.workerType()).isEqualTo("AI");
            assertThat(failureEvent.errorMessage()).isEqualTo(errorMessage);
            // sagaVersion will be 1L (default) since AiWorkerListener doesn't propagate it yet
        }

        @Test
        @DisplayName("Should publish failure event with default sagaVersion")
        void shouldPublishFailureEventWithDefaultSagaVersion() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            long sagaVersion = 3L;
            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "AI", "{}", sagaVersion, "ADMIN");
            String payload = objectMapper.writeValueAsString(requestEvent);

            doThrow(new RuntimeException("Test error"))
                .when(aiWorkerUseCase).processAiEnrichment(any(), any());

            // When
            listener.handleAiRequest(payload);

            // Then
            ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(eq("ai.failed"), eventCaptor.capture());

            WorkerFailedEvent failureEvent = objectMapper.readValue(eventCaptor.getValue(), WorkerFailedEvent.class);
            // NOTE: AiWorkerListener currently doesn't propagate sagaVersion (uses old 3-param constructor)
            assertThat(failureEvent.sagaVersion()).isEqualTo(1L); // Default value
        }

        @Test
        @DisplayName("Should handle different exception types")
        void shouldHandleDifferentExceptionTypes() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "AI", "{}", 1L, "REGISTERED");
            String payload = objectMapper.writeValueAsString(requestEvent);

            doThrow(new IllegalStateException("Invalid state"))
                .when(aiWorkerUseCase).processAiEnrichment(any(), any());

            // When
            listener.handleAiRequest(payload);

            // Then
            ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(eq("ai.failed"), eventCaptor.capture());

            WorkerFailedEvent failureEvent = objectMapper.readValue(eventCaptor.getValue(), WorkerFailedEvent.class);
            assertThat(failureEvent.errorMessage()).contains("Invalid state");
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
            listener.handleAiRequest(invalidPayload);

            // Then
            verify(aiWorkerUseCase, never()).processAiEnrichment(any(), any());
            verify(kafkaTemplate, never()).send(anyString(), anyString());
        }

        @Test
        @DisplayName("Should skip processing when payload is empty")
        void shouldSkipProcessingWhenPayloadEmpty() {
            // Given
            String emptyPayload = "";

            // When
            listener.handleAiRequest(emptyPayload);

            // Then
            verify(aiWorkerUseCase, never()).processAiEnrichment(any(), any());
            verify(kafkaTemplate, never()).send(anyString(), anyString());
        }

        @Test
        @DisplayName("Should skip processing when payload is null")
        void shouldSkipProcessingWhenPayloadNull() {
            // Given
            String nullPayload = null;

            // When
            listener.handleAiRequest(nullPayload);

            // Then
            verify(aiWorkerUseCase, never()).processAiEnrichment(any(), any());
            verify(kafkaTemplate, never()).send(anyString(), anyString());
        }

        @Test
        @DisplayName("Should skip processing when payload is malformed")
        void shouldSkipProcessingWhenPayloadMalformed() {
            // Given
            String malformedPayload = "{\"itineraryId\":\"not-a-uuid\"}";

            // When
            listener.handleAiRequest(malformedPayload);

            // Then
            verify(aiWorkerUseCase, never()).processAiEnrichment(any(), any());
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
            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "AI", "{}", 1L, "REGISTERED");
            String payload = objectMapper.writeValueAsString(requestEvent);

            doThrow(new RuntimeException("Kafka unavailable"))
                .when(kafkaTemplate).send(anyString(), anyString());

            // When
            listener.handleAiRequest(payload);

            // Then
            verify(aiWorkerUseCase).processAiEnrichment(itineraryId, "{}");
            verify(kafkaTemplate).send(eq("ai.completed"), anyString());
            // Should not propagate exception
        }

        @Test
        @DisplayName("Should handle Kafka publish error for failure event")
        void shouldHandleKafkaPublishErrorForFailureEvent() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            WorkerRequestEvent requestEvent = new WorkerRequestEvent(itineraryId, "AI", "{}", 1L, "REGISTERED");
            String payload = objectMapper.writeValueAsString(requestEvent);

            doThrow(new RuntimeException("Processing error"))
                .when(aiWorkerUseCase).processAiEnrichment(any(), any());
            doThrow(new RuntimeException("Kafka unavailable"))
                .when(kafkaTemplate).send(anyString(), anyString());

            // When
            listener.handleAiRequest(payload);

            // Then
            verify(kafkaTemplate).send(eq("ai.failed"), anyString());
            // Should not propagate exception
        }
    }

    @Nested
    @DisplayName("Backward Compatibility Tests")
    class BackwardCompatibilityTests {

        @Test
        @DisplayName("Should handle request event without sagaVersion (default to 1)")
        void shouldHandleRequestEventWithoutSagaVersion() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            WorkerRequestEvent oldFormatEvent = new WorkerRequestEvent(itineraryId, "AI", "{}");
            String payload = objectMapper.writeValueAsString(oldFormatEvent);

            // When
            listener.handleAiRequest(payload);

            // Then
            verify(aiWorkerUseCase).processAiEnrichment(itineraryId, "{}");

            ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(eq("ai.completed"), eventCaptor.capture());

            WorkerCompleteEvent completionEvent = objectMapper.readValue(eventCaptor.getValue(), WorkerCompleteEvent.class);
            assertThat(completionEvent.sagaVersion()).isEqualTo(1L); // Default value
        }

        @Test
        @DisplayName("Should handle request event without userType (default to ANONYMOUS)")
        void shouldHandleRequestEventWithoutUserType() throws Exception {
            // Given
            UUID itineraryId = UUID.randomUUID();
            WorkerRequestEvent oldFormatEvent = new WorkerRequestEvent(itineraryId, "AI", "{}", 2L);
            String payload = objectMapper.writeValueAsString(oldFormatEvent);

            // When
            listener.handleAiRequest(payload);

            // Then
            verify(aiWorkerUseCase).processAiEnrichment(itineraryId, "{}");
            verify(kafkaTemplate).send(eq("ai.completed"), anyString());
        }
    }
}
