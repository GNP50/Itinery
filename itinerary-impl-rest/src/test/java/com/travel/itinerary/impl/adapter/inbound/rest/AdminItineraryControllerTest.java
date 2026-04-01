package com.travel.itinerary.impl.adapter.inbound.rest;

import com.travel.itinerary.api.dto.AdminDTO;
import com.travel.itinerary.api.port.inbound.AdminItineraryUseCase;
import com.travel.itinerary.api.port.inbound.ItinerarySagaQueryUseCase;
import com.travel.itinerary.impl.config.AsyncConfig;
import com.travel.saga.dto.SagaInstanceDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for AdminItineraryController.
 * Tests all admin REST endpoints for itinerary management.
 */
@WebMvcTest(AdminItineraryController.class)
@Import(AsyncConfig.class)
@DisplayName("AdminItineraryController Tests")
class AdminItineraryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminItineraryUseCase adminItineraryUseCase;

    @MockBean
    private ItinerarySagaQueryUseCase sagaQueryUseCase;

    private UUID testItineraryId;
    private UUID testSagaId;

    @BeforeEach
    void setUp() {
        testItineraryId = UUID.randomUUID();
        testSagaId = UUID.randomUUID();
    }

    // =========================================================================
    // LIST ALL ITINERARIES (ADMIN)
    // =========================================================================

    @Nested
    @DisplayName("GET /api/v1/admin/itineraries - List All Itineraries")
    class ListAllItinerariesTests {

        @Test
        @DisplayName("Should return paginated list of itineraries for ADMIN user")
        void shouldReturnPaginatedItinerariesForAdmin() throws Exception {
            // Given
            var itinerary1 = new AdminDTO.ItinerarySummary(
                UUID.randomUUID(),
                "Trip to Rome",
                "COMPLETED",
                UUID.randomUUID(),
                "john.doe@example.com",
                "John Doe",
                "USER",
                Instant.parse("2026-03-15T10:00:00Z"),
                null,
                "token-123"
            );

            var itinerary2 = new AdminDTO.ItinerarySummary(
                UUID.randomUUID(),
                "Paris Adventure",
                "PROCESSING",
                UUID.randomUUID(),
                "jane.smith@example.com",
                "Jane Smith",
                "USER",
                Instant.parse("2026-03-20T14:30:00Z"),
                0,
                "token-456"
            );

            var pagedResponse = new AdminDTO.PagedItineraries(
                List.of(itinerary1, itinerary2),
                2,
                1,
                0,
                20
            );

            when(adminItineraryUseCase.listAllItineraries(0, 20)).thenReturn(pagedResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/admin/itineraries")
                    .param("page", "0")
                    .param("size", "20")
                    .with(jwt().jwt(builder -> builder
                        .claim("userType", "ADMIN")
                        .claim("sub", "admin@example.com")))
                    .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].title", is("Trip to Rome")))
                .andExpect(jsonPath("$.items[0].status", is("COMPLETED")))
                .andExpect(jsonPath("$.items[0].ownerEmail", is("john.doe@example.com")))
                .andExpect(jsonPath("$.items[1].title", is("Paris Adventure")))
                .andExpect(jsonPath("$.items[1].ownerEmail", is("jane.smith@example.com")))
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.size", is(20)))
                .andExpect(jsonPath("$.totalElements", is(2)))
                .andExpect(jsonPath("$.totalPages", is(1)));

            verify(adminItineraryUseCase, times(1)).listAllItineraries(0, 20);
        }

        @Test
        @DisplayName("Should use default pagination parameters when not specified")
        void shouldUseDefaultPaginationParameters() throws Exception {
            // Given
            var emptyResponse = new AdminDTO.PagedItineraries(
                List.of(),
                0,
                0,
                0,
                20
            );

            when(adminItineraryUseCase.listAllItineraries(0, 20)).thenReturn(emptyResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/admin/itineraries")
                    .with(jwt().jwt(builder -> builder
                        .claim("userType", "ADMIN")))
                    .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.size", is(20)));

            verify(adminItineraryUseCase, times(1)).listAllItineraries(0, 20);
        }

        @Test
        @DisplayName("Should return 403 when user is not ADMIN")
        void shouldReturn403WhenNotAdmin() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/v1/admin/itineraries")
                    .with(jwt().jwt(builder -> builder
                        .claim("userType", "USER")))
                    .with(csrf()))
                .andDo(print())
                .andExpect(status().isForbidden());

            verify(adminItineraryUseCase, never()).listAllItineraries(anyInt(), anyInt());
        }

        @Test
        @DisplayName("Should return 403 when userType claim is missing")
        void shouldReturn403WhenUserTypeClaimMissing() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/v1/admin/itineraries")
                    .with(jwt().jwt(builder -> builder
                        .claim("sub", "user@example.com")))
                    .with(csrf()))
                .andDo(print())
                .andExpect(status().isForbidden());

            verify(adminItineraryUseCase, never()).listAllItineraries(anyInt(), anyInt());
        }

        @Test
        @DisplayName("Should handle custom page size")
        void shouldHandleCustomPageSize() throws Exception {
            // Given
            var response = new AdminDTO.PagedItineraries(
                List.of(),
                0,
                0,
                2,
                50
            );

            when(adminItineraryUseCase.listAllItineraries(2, 50)).thenReturn(response);

            // When & Then
            mockMvc.perform(get("/api/v1/admin/itineraries")
                    .param("page", "2")
                    .param("size", "50")
                    .with(jwt().jwt(builder -> builder
                        .claim("userType", "ADMIN")))
                    .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page", is(2)))
                .andExpect(jsonPath("$.size", is(50)));

            verify(adminItineraryUseCase, times(1)).listAllItineraries(2, 50);
        }
    }

    // =========================================================================
    // GET ITINERARY SAGA LIFECYCLE (ADMIN)
    // =========================================================================

    @Nested
    @DisplayName("GET /api/v1/admin/itineraries/{itineraryId}/saga - Get Saga Lifecycle")
    class GetItinerarySagaLifecycleTests {

        @Test
        @DisplayName("Should return saga lifecycle for ADMIN user")
        void shouldReturnSagaLifecycleForAdmin() throws Exception {
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

            when(sagaQueryUseCase.getSagasByItinerary(testItineraryId))
                .thenReturn(List.of(saga1, saga2));

            // When & Then
            mockMvc.perform(get("/api/v1/admin/itineraries/{itineraryId}/saga", testItineraryId)
                    .with(jwt().jwt(builder -> builder
                        .claim("userType", "ADMIN")))
                    .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].currentState", is("COMPLETED")))
                .andExpect(jsonPath("$[1].currentState", is("RUNNING")));

            verify(sagaQueryUseCase, times(1)).getSagasByItinerary(testItineraryId);
        }

        @Test
        @DisplayName("Should return empty list when no sagas exist")
        void shouldReturnEmptyListWhenNoSagas() throws Exception {
            // Given
            when(sagaQueryUseCase.getSagasByItinerary(testItineraryId))
                .thenReturn(List.of());

            // When & Then
            mockMvc.perform(get("/api/v1/admin/itineraries/{itineraryId}/saga", testItineraryId)
                    .with(jwt().jwt(builder -> builder
                        .claim("userType", "ADMIN")))
                    .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

            verify(sagaQueryUseCase, times(1)).getSagasByItinerary(testItineraryId);
        }

        @Test
        @DisplayName("Should return 403 when user is not ADMIN")
        void shouldReturn403WhenNotAdmin() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/v1/admin/itineraries/{itineraryId}/saga", testItineraryId)
                    .with(jwt().jwt(builder -> builder
                        .claim("userType", "USER")))
                    .with(csrf()))
                .andExpect(status().isForbidden());

            verify(sagaQueryUseCase, never()).getSagasByItinerary(any());
        }

        @Test
        @DisplayName("Should return 500 when saga query fails")
        void shouldReturn500OnQueryFailure() throws Exception {
            // Given
            when(sagaQueryUseCase.getSagasByItinerary(testItineraryId))
                .thenThrow(new RuntimeException("Database connection failed"));

            // When & Then
            mockMvc.perform(get("/api/v1/admin/itineraries/{itineraryId}/saga", testItineraryId)
                    .with(jwt().jwt(builder -> builder
                        .claim("userType", "ADMIN")))
                    .with(csrf()))
                .andDo(print())
                .andExpect(status().isInternalServerError());

            verify(sagaQueryUseCase, times(1)).getSagasByItinerary(testItineraryId);
        }
    }

    // =========================================================================
    // GET SAGA INSTANCE BY ID (ADMIN)
    // =========================================================================

    @Nested
    @DisplayName("GET /api/v1/admin/saga/{sagaId} - Get Saga Instance")
    class GetSagaInstanceTests {

        @Test
        @DisplayName("Should return saga instance for ADMIN user")
        void shouldReturnSagaInstanceForAdmin() throws Exception {
            // Given
            var sagaInstance = SagaInstanceDto.builder()
                .id(testSagaId)
                .itineraryId(testItineraryId)
                .currentState("COMPLETED")
                .completedSteps(java.util.Set.of("GEOCODE", "ROUTE", "POI", "AI"))
                .version(5L)
                .retryCount(0)
                .maxRetries(3)
                .createdAt(Instant.parse("2026-03-30T10:00:00Z"))
                .updatedAt(Instant.parse("2026-03-30T10:15:00Z"))
                .build();

            when(sagaQueryUseCase.getSagaInstance(testSagaId))
                .thenReturn(Optional.of(sagaInstance));

            // When & Then
            mockMvc.perform(get("/api/v1/admin/saga/{sagaId}", testSagaId)
                    .with(jwt().jwt(builder -> builder
                        .claim("userType", "ADMIN")))
                    .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(testSagaId.toString())))
                .andExpect(jsonPath("$.itineraryId", is(testItineraryId.toString())))
                .andExpect(jsonPath("$.currentState", is("COMPLETED")))
                .andExpect(jsonPath("$.version", is(5)));

            verify(sagaQueryUseCase, times(1)).getSagaInstance(testSagaId);
        }

        @Test
        @DisplayName("Should return 404 when saga instance not found")
        void shouldReturn404WhenSagaNotFound() throws Exception {
            // Given
            when(sagaQueryUseCase.getSagaInstance(testSagaId))
                .thenReturn(Optional.empty());

            // When & Then
            mockMvc.perform(get("/api/v1/admin/saga/{sagaId}", testSagaId)
                    .with(jwt().jwt(builder -> builder
                        .claim("userType", "ADMIN")))
                    .with(csrf()))
                .andDo(print())
                .andExpect(status().isNotFound());

            verify(sagaQueryUseCase, times(1)).getSagaInstance(testSagaId);
        }

        @Test
        @DisplayName("Should return 403 when user is not ADMIN")
        void shouldReturn403WhenNotAdmin() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/v1/admin/saga/{sagaId}", testSagaId)
                    .with(jwt().jwt(builder -> builder
                        .claim("userType", "USER")))
                    .with(csrf()))
                .andExpect(status().isForbidden());

            verify(sagaQueryUseCase, never()).getSagaInstance(any());
        }

        @Test
        @DisplayName("Should return 500 when saga query fails")
        void shouldReturn500OnQueryFailure() throws Exception {
            // Given
            when(sagaQueryUseCase.getSagaInstance(testSagaId))
                .thenThrow(new RuntimeException("Service unavailable"));

            // When & Then
            mockMvc.perform(get("/api/v1/admin/saga/{sagaId}", testSagaId)
                    .with(jwt().jwt(builder -> builder
                        .claim("userType", "ADMIN")))
                    .with(csrf()))
                .andDo(print())
                .andExpect(status().isInternalServerError());

            verify(sagaQueryUseCase, times(1)).getSagaInstance(testSagaId);
        }
    }
}

