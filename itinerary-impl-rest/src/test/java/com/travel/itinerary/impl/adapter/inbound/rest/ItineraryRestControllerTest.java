package com.travel.itinerary.impl.adapter.inbound.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.itinerary.api.dto.ItineraryDTO;
import com.travel.itinerary.api.dto.PositionUpdateDTO;
import com.travel.itinerary.api.dto.StepDTO;
import com.travel.itinerary.api.port.inbound.*;
import com.travel.saga.dto.SagaInstanceDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for ItineraryRestController.
 * Tests all REST endpoints for itinerary management.
 */
@WebMvcTest(ItineraryRestController.class)
@DisplayName("ItineraryRestController Tests")
class ItineraryRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CreateItineraryUseCase createUseCase;

    @MockBean
    private GetItineraryUseCase getUseCase;

    @MockBean
    private UpdateItineraryUseCase updateUseCase;

    @MockBean
    private DeleteItineraryUseCase deleteUseCase;

    @MockBean
    private UpdatePositionUseCase positionUseCase;

    @MockBean
    private ListItinerariesUseCase listUseCase;

    @MockBean
    private ItinerarySagaQueryUseCase sagaQueryUseCase;

    @MockBean
    private CloneItineraryUseCase cloneUseCase;

    private UUID testItineraryId;
    private String testAccessToken;

    @BeforeEach
    void setUp() {
        testItineraryId = UUID.randomUUID();
        testAccessToken = "test-access-token-" + UUID.randomUUID();
    }

    // =========================================================================
    // LIST MY ITINERARIES
    // =========================================================================

    @Nested
    @DisplayName("GET /api/v1/itineraries - List My Itineraries")
    class ListMyItinerariesTests {

        @Test
        @WithMockUser
        @DisplayName("Should return list of itineraries for authenticated user")
        void shouldReturnListOfItineraries() throws Exception {
            // Given
            var summary1 = new ItineraryDTO.ItinerarySummary(
                UUID.randomUUID(),
                "Trip to Rome",
                "COMPLETED",
                "DRIVING",
                5,
                "2026-03-15T10:00:00Z"
            );
            var summary2 = new ItineraryDTO.ItinerarySummary(
                UUID.randomUUID(),
                "Paris Adventure",
                "PROCESSING",
                "WALKING",
                8,
                "2026-03-20T14:30:00Z"
            );
            var listResponse = new ItineraryDTO.ListResponse(List.of(summary1, summary2));

            when(listUseCase.listMyItineraries()).thenReturn(listResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/itineraries")
                    .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.itineraries", hasSize(2)))
                .andExpect(jsonPath("$.itineraries[0].title", is("Trip to Rome")))
                .andExpect(jsonPath("$.itineraries[0].status", is("COMPLETED")))
                .andExpect(jsonPath("$.itineraries[0].stepCount", is(5)))
                .andExpect(jsonPath("$.itineraries[1].title", is("Paris Adventure")))
                .andExpect(jsonPath("$.itineraries[1].status", is("PROCESSING")))
                .andExpect(jsonPath("$.itineraries[1].stepCount", is(8)));

            verify(listUseCase, times(1)).listMyItineraries();
        }

        @Test
        @WithMockUser
        @DisplayName("Should return empty list when user has no itineraries")
        void shouldReturnEmptyList() throws Exception {
            // Given
            var emptyResponse = new ItineraryDTO.ListResponse(List.of());
            when(listUseCase.listMyItineraries()).thenReturn(emptyResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/itineraries")
                    .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itineraries", hasSize(0)));

            verify(listUseCase, times(1)).listMyItineraries();
        }
    }

    // =========================================================================
    // CREATE ITINERARY
    // =========================================================================

    @Nested
    @DisplayName("POST /api/v1/itineraries - Create Itinerary")
    class CreateItineraryTests {

        @Test
        @WithMockUser
        @DisplayName("Should create itinerary and return 202 Accepted")
        void shouldCreateItinerary() throws Exception {
            // Given
            var step1 = new StepDTO.Request("Colosseum", "Visit the iconic Colosseum", null, null);
            var step2 = new StepDTO.Request("Trevi Fountain", "Throw a coin in the fountain", null, null);
            var request = new ItineraryDTO.Request(
                "Roman Holiday",
                "A wonderful trip to Rome",
                "WALKING",
                List.of(step1, step2),
                null
            );

            var createResponse = new ItineraryDTO.CreateResponse(
                testItineraryId,
                testAccessToken,
                "PENDING",
                0,
                "2026-04-01T10:00:00Z"
            );

            when(createUseCase.create(any(ItineraryDTO.Request.class))).thenReturn(createResponse);

            // When & Then
            mockMvc.perform(post("/api/v1/itineraries")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(testItineraryId.toString())))
                .andExpect(jsonPath("$.accessToken", is(testAccessToken)))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.queuePosition", is(0)));

            verify(createUseCase, times(1)).create(any(ItineraryDTO.Request.class));
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 400 when request has validation errors")
        void shouldReturn400OnValidationError() throws Exception {
            // Given - invalid request (missing title)
            var invalidRequest = """
                {
                    "description": "A trip",
                    "travelMode": "DRIVING",
                    "steps": []
                }
                """;

            // When & Then
            mockMvc.perform(post("/api/v1/itineraries")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(invalidRequest))
                .andExpect(status().isBadRequest());

            verify(createUseCase, never()).create(any());
        }
    }

    // =========================================================================
    // GET ITINERARY BY ID
    // =========================================================================

    @Nested
    @DisplayName("GET /api/v1/itineraries/{id} - Get Itinerary By ID")
    class GetItineraryByIdTests {

        @Test
        @DisplayName("Should return itinerary when valid token is provided")
        void shouldReturnItinerary() throws Exception {
            // Given
            var stepResponse = new StepDTO.Response(
                UUID.randomUUID(),
                1,
                "Colosseum",
                "Rome",
                "Rome",
                "Lazio",
                "Italy",
                "IT",
                new BigDecimal("41.8902"),
                new BigDecimal("12.4922"),
                12345678L,
                null,
                "The Colosseum is an ancient amphitheatre",
                null,
                null,
                null,
                null,
                null,
                "ENRICHED",
                null,
                null
            );

            var response = new ItineraryDTO.Response(
                testItineraryId,
                testAccessToken,
                "Roman Holiday",
                "A wonderful trip to Rome",
                "COMPLETED",
                null,
                null,
                "WALKING",
                new BigDecimal("15.5"),
                120,
                0,
                List.of(stepResponse),
                null,
                null,
                "2026-03-15T10:00:00Z",
                "2026-03-15T12:00:00Z"
            );

            when(getUseCase.getById(eq(testItineraryId), eq(testAccessToken))).thenReturn(response);

            // When & Then
            mockMvc.perform(get("/api/v1/itineraries/{id}", testItineraryId)
                    .param("token", testAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(testItineraryId.toString())))
                .andExpect(jsonPath("$.title", is("Roman Holiday")))
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.steps", hasSize(1)));

            verify(getUseCase, times(1)).getById(testItineraryId, testAccessToken);
        }

        @Test
        @WithMockUser
        @DisplayName("Should return itinerary when authenticated with JWT")
        void shouldReturnItineraryWithJWT() throws Exception {
            // Given
            var response = new ItineraryDTO.Response(
                testItineraryId,
                testAccessToken,
                "Roman Holiday",
                "A wonderful trip to Rome",
                "COMPLETED",
                null,
                null,
                "WALKING",
                new BigDecimal("15.5"),
                120,
                0,
                List.of(),
                null,
                null,
                "2026-03-15T10:00:00Z",
                "2026-03-15T12:00:00Z"
            );

            when(getUseCase.getById(eq(testItineraryId), isNull())).thenReturn(response);

            // When & Then
            mockMvc.perform(get("/api/v1/itineraries/{id}", testItineraryId)
                    .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(testItineraryId.toString())));

            verify(getUseCase, times(1)).getById(testItineraryId, null);
        }
    }

    // =========================================================================
    // GET ITINERARY STATUS
    // =========================================================================

    @Nested
    @DisplayName("GET /api/v1/itineraries/{id}/status - Get Status")
    class GetItineraryStatusTests {

        @Test
        @DisplayName("Should return status with progress information")
        void shouldReturnStatus() throws Exception {
            // Given
            var statusResponse = new ItineraryDTO.StatusResponse(
                testItineraryId,
                "PROCESSING",
                null,
                "2026-04-01T15:00:00Z",
                60
            );

            when(getUseCase.getStatus(eq(testItineraryId), eq(testAccessToken)))
                .thenReturn(statusResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/itineraries/{id}/status", testItineraryId)
                    .param("token", testAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(testItineraryId.toString())))
                .andExpect(jsonPath("$.status", is("PROCESSING")))
                .andExpect(jsonPath("$.progressPercent", is(60)));

            verify(getUseCase, times(1)).getStatus(testItineraryId, testAccessToken);
        }
    }

    // =========================================================================
    // UPDATE ITINERARY
    // =========================================================================

    @Nested
    @DisplayName("PUT /api/v1/itineraries/{id} - Update Itinerary")
    class UpdateItineraryTests {

        @Test
        @DisplayName("Should update itinerary and return updated data")
        void shouldUpdateItinerary() throws Exception {
            // Given
            var updateRequest = new ItineraryDTO.Request(
                "Updated Title",
                "Updated description",
                "DRIVING",
                List.of(new StepDTO.Request("New Place", null, null, null)),
                null
            );

            var response = new ItineraryDTO.Response(
                testItineraryId,
                testAccessToken,
                "Updated Title",
                "Updated description",
                "PENDING",
                0,
                null,
                "DRIVING",
                null,
                null,
                0,
                List.of(),
                null,
                null,
                "2026-03-15T10:00:00Z",
                "2026-03-30T10:00:00Z"
            );

            when(updateUseCase.update(eq(testItineraryId), eq(testAccessToken), any(ItineraryDTO.Request.class)))
                .thenReturn(response);

            // When & Then
            mockMvc.perform(put("/api/v1/itineraries/{id}", testItineraryId)
                    .param("token", testAccessToken)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(testItineraryId.toString())))
                .andExpect(jsonPath("$.title", is("Updated Title")))
                .andExpect(jsonPath("$.description", is("Updated description")));

            verify(updateUseCase, times(1))
                .update(eq(testItineraryId), eq(testAccessToken), any(ItineraryDTO.Request.class));
        }

        @Test
        @DisplayName("Should return 400 when token parameter is missing")
        void shouldReturn400WhenTokenMissing() throws Exception {
            // Given
            var updateRequest = new ItineraryDTO.Request(
                "Updated Title",
                "Updated description",
                "DRIVING",
                List.of(new StepDTO.Request("New Place", null, null, null)),
                null
            );

            // When & Then
            mockMvc.perform(put("/api/v1/itineraries/{id}", testItineraryId)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest());

            verify(updateUseCase, never()).update(any(), any(), any());
        }
    }

    // =========================================================================
    // DELETE ITINERARY
    // =========================================================================

    @Nested
    @DisplayName("DELETE /api/v1/itineraries/{id} - Delete Itinerary")
    class DeleteItineraryTests {

        @Test
        @DisplayName("Should delete itinerary and return 204")
        void shouldDeleteItinerary() throws Exception {
            // Given
            doNothing().when(deleteUseCase).delete(testItineraryId, testAccessToken);

            // When & Then
            mockMvc.perform(delete("/api/v1/itineraries/{id}", testItineraryId)
                    .param("token", testAccessToken)
                    .with(csrf()))
                .andDo(print())
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

            verify(deleteUseCase, times(1)).delete(testItineraryId, testAccessToken);
        }

        @Test
        @WithMockUser
        @DisplayName("Should delete itinerary using JWT authentication")
        void shouldDeleteWithJWT() throws Exception {
            // Given
            doNothing().when(deleteUseCase).delete(testItineraryId, null);

            // When & Then
            mockMvc.perform(delete("/api/v1/itineraries/{id}", testItineraryId)
                    .with(csrf()))
                .andExpect(status().isNoContent());

            verify(deleteUseCase, times(1)).delete(testItineraryId, null);
        }
    }

    // =========================================================================
    // UPDATE POSITION
    // =========================================================================

    @Nested
    @DisplayName("PATCH /api/v1/itineraries/{id}/position - Update Position")
    class UpdatePositionTests {

        @Test
        @DisplayName("Should update position by step index")
        void shouldUpdatePositionByIndex() throws Exception {
            // Given
            var positionRequest = new PositionUpdateDTO.Request(2, null, null);
            var positionResponse = new PositionUpdateDTO.Response(
                testItineraryId,
                2,
                "2026-03-30T10:00:00Z"
            );

            when(positionUseCase.updatePosition(eq(testItineraryId), eq(testAccessToken), any()))
                .thenReturn(positionResponse);

            // When & Then
            mockMvc.perform(patch("/api/v1/itineraries/{id}/position", testItineraryId)
                    .param("token", testAccessToken)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(positionRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(testItineraryId.toString())))
                .andExpect(jsonPath("$.currentStepIndex", is(2)));

            verify(positionUseCase, times(1)).updatePosition(eq(testItineraryId), eq(testAccessToken), any());
        }

        @Test
        @DisplayName("Should update position by coordinates")
        void shouldUpdatePositionByCoordinates() throws Exception {
            // Given
            var positionRequest = new PositionUpdateDTO.Request(
                null,
                new BigDecimal("41.8902"),
                new BigDecimal("12.4922")
            );
            var positionResponse = new PositionUpdateDTO.Response(
                testItineraryId,
                3,
                "2026-03-30T10:05:00Z"
            );

            when(positionUseCase.updatePosition(eq(testItineraryId), eq(testAccessToken), any()))
                .thenReturn(positionResponse);

            // When & Then
            mockMvc.perform(patch("/api/v1/itineraries/{id}/position", testItineraryId)
                    .param("token", testAccessToken)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(positionRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStepIndex", is(3)));

            verify(positionUseCase, times(1)).updatePosition(eq(testItineraryId), eq(testAccessToken), any());
        }
    }

    // =========================================================================
    // GET SAGA LIFECYCLE
    // =========================================================================

    @Nested
    @DisplayName("GET /api/v1/itineraries/{id}/saga - Get Saga Lifecycle")
    class GetSagaLifecycleTests {

        @Test
        @DisplayName("Should return saga lifecycle information")
        void shouldReturnSagaLifecycle() throws Exception {
            // Given
            var saga1 = SagaInstanceDto.builder()
                .id(UUID.randomUUID())
                .itineraryId(testItineraryId)
                .currentState("COMPLETED")
                .completedSteps(java.util.Set.of("GEOCODE", "ROUTE", "POI", "AI"))
                .build();

            var saga2 = SagaInstanceDto.builder()
                .id(UUID.randomUUID())
                .itineraryId(testItineraryId)
                .currentState("RUNNING")
                .completedSteps(java.util.Set.of("GEOCODE"))
                .build();

            var response = new ItineraryDTO.Response(
                testItineraryId,
                testAccessToken,
                "Test Itinerary",
                "Description",
                "PROCESSING",
                null,
                null,
                "DRIVING",
                null,
                null,
                0,
                List.of(),
                null,
                null,
                "2026-03-15T10:00:00Z",
                "2026-03-30T10:00:00Z"
            );

            when(getUseCase.getById(eq(testItineraryId), eq(testAccessToken))).thenReturn(response);
            when(sagaQueryUseCase.getSagasByItinerary(testItineraryId)).thenReturn(List.of(saga1, saga2));

            // When & Then
            mockMvc.perform(get("/api/v1/itineraries/{id}/saga", testItineraryId)
                    .param("token", testAccessToken))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].currentState", is("COMPLETED")))
                .andExpect(jsonPath("$[1].currentState", is("RUNNING")));

            verify(getUseCase, times(1)).getById(testItineraryId, testAccessToken);
            verify(sagaQueryUseCase, times(1)).getSagasByItinerary(testItineraryId);
        }
    }

    // =========================================================================
    // CLONE ITINERARY
    // =========================================================================

    @Nested
    @DisplayName("POST /api/v1/itineraries/{id}/clone - Clone Itinerary")
    class CloneItineraryTests {

        @Test
        @DisplayName("Should clone itinerary and return 202 Accepted")
        void shouldCloneItinerary() throws Exception {
            // Given
            UUID newItineraryId = UUID.randomUUID();
            String newAccessToken = "new-access-token";

            var cloneResponse = new ItineraryDTO.CreateResponse(
                newItineraryId,
                newAccessToken,
                "PENDING",
                0,
                "2026-04-01T10:00:00Z"
            );

            when(cloneUseCase.cloneItinerary(eq(testItineraryId), eq(testAccessToken)))
                .thenReturn(cloneResponse);

            // When & Then
            mockMvc.perform(post("/api/v1/itineraries/{id}/clone", testItineraryId)
                    .param("token", testAccessToken)
                    .with(csrf()))
                .andDo(print())
                .andExpect(status().isAccepted())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(newItineraryId.toString())))
                .andExpect(jsonPath("$.accessToken", is(newAccessToken)))
                .andExpect(jsonPath("$.status", is("PENDING")));

            verify(cloneUseCase, times(1)).cloneItinerary(testItineraryId, testAccessToken);
        }

        @Test
        @WithMockUser
        @DisplayName("Should clone itinerary using JWT authentication")
        void shouldCloneWithJWT() throws Exception {
            // Given
            UUID newItineraryId = UUID.randomUUID();
            String newAccessToken = "new-access-token";

            var cloneResponse = new ItineraryDTO.CreateResponse(
                newItineraryId,
                newAccessToken,
                "PENDING",
                0,
                "2026-04-01T10:00:00Z"
            );

            when(cloneUseCase.cloneItinerary(eq(testItineraryId), isNull()))
                .thenReturn(cloneResponse);

            // When & Then
            mockMvc.perform(post("/api/v1/itineraries/{id}/clone", testItineraryId)
                    .with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id", is(newItineraryId.toString())));

            verify(cloneUseCase, times(1)).cloneItinerary(testItineraryId, null);
        }
    }
}

