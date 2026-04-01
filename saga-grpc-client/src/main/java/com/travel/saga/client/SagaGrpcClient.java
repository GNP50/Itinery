package com.travel.saga.client;

import com.travel.saga.dto.SagaInstanceDto;
import com.travel.saga.dto.SagaOperationResult;
import com.travel.saga.grpc.v1.*;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * gRPC client for Saga orchestrator.
 * Used by other services to query saga state and trigger operations.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SagaGrpcClient {

    @GrpcClient("saga-service")
    private SagaServiceGrpc.SagaServiceBlockingStub sagaServiceStub;

    /**
     * Get a saga instance by its ID.
     */
    public Optional<SagaInstanceDto> getSagaInstance(UUID sagaId) {
        log.debug("Calling gRPC getSagaInstance for saga: {}", sagaId);

        try {
            var request = GetSagaInstanceRequest.newBuilder()
                .setSagaId(sagaId.toString())
                .build();

            var response = sagaServiceStub.getSagaInstance(request);
            return Optional.of(fromProto(response));

        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                log.debug("Saga not found: {}", sagaId);
                return Optional.empty();
            }
            log.error("Error getting saga instance: {}", sagaId, e);
            throw new RuntimeException("Failed to get saga instance", e);
        }
    }

    /**
     * Get saga instances by itinerary ID.
     */
    public List<SagaInstanceDto> getSagasByItinerary(UUID itineraryId) {
        log.debug("Calling gRPC getSagaByItinerary for itinerary: {}", itineraryId);

        try {
            var request = GetSagaByItineraryRequest.newBuilder()
                .setItineraryId(itineraryId.toString())
                .build();

            var response = sagaServiceStub.getSagaByItinerary(request);
            return response.getSagaInstancesList().stream()
                .map(this::fromProto)
                .collect(Collectors.toList());

        } catch (StatusRuntimeException e) {
            log.error("Error getting sagas by itinerary: {}", itineraryId, e);
            throw new RuntimeException("Failed to get sagas by itinerary", e);
        }
    }

    /**
     * List saga instances with pagination.
     */
    public List<SagaInstanceDto> listSagaInstances(int offset, int limit) {
        return listSagaInstances(offset, limit, null);
    }

    /**
     * List saga instances with pagination and state filter.
     */
    public List<SagaInstanceDto> listSagaInstances(int offset, int limit, String stateFilter) {
        log.debug("Calling gRPC listSagaInstances with offset: {}, limit: {}, state: {}", offset, limit, stateFilter);

        try {
            var requestBuilder = ListSagaInstancesRequest.newBuilder()
                .setOffset(offset)
                .setLimit(limit);

            if (stateFilter != null) {
                requestBuilder.setStateFilter(stateToProto(stateFilter));
            }

            var response = sagaServiceStub.listSagaInstances(requestBuilder.build());
            return response.getSagaInstancesList().stream()
                .map(this::fromProto)
                .collect(Collectors.toList());

        } catch (StatusRuntimeException e) {
            log.error("Error listing saga instances", e);
            throw new RuntimeException("Failed to list saga instances", e);
        }
    }

    /**
     * Retry a failed saga.
     */
    public SagaOperationResult retrySaga(UUID sagaId) {
        log.info("Calling gRPC retrySaga for saga: {}", sagaId);

        try {
            var request = RetrySagaRequest.newBuilder()
                .setSagaId(sagaId.toString())
                .build();

            var response = sagaServiceStub.retrySaga(request);
            return SagaOperationResult.builder()
                .success(response.getSuccess())
                .message(response.getMessage())
                .build();

        } catch (StatusRuntimeException e) {
            log.error("Error retrying saga: {}", sagaId, e);
            return SagaOperationResult.failure("gRPC error: " + e.getStatus().getDescription());
        }
    }

    /**
     * Trigger compensation for a saga.
     */
    public SagaOperationResult compensateSaga(UUID sagaId) {
        log.info("Calling gRPC compensateSaga for saga: {}", sagaId);

        try {
            var request = CompensateSagaRequest.newBuilder()
                .setSagaId(sagaId.toString())
                .build();

            var response = sagaServiceStub.compensateSaga(request);
            return SagaOperationResult.builder()
                .success(response.getSuccess())
                .message(response.getMessage())
                .build();

        } catch (StatusRuntimeException e) {
            log.error("Error compensating saga: {}", sagaId, e);
            return SagaOperationResult.failure("gRPC error: " + e.getStatus().getDescription());
        }
    }

    private SagaInstanceDto fromProto(SagaInstanceResponse proto) {
        return SagaInstanceDto.builder()
            .id(UUID.fromString(proto.getId()))
            .itineraryId(UUID.fromString(proto.getItineraryId()))
            .currentState(stateFromProto(proto.getCurrentState()))
            .completedSteps(proto.getCompletedStepsList().stream()
                .map(this::stepFromProto)
                .collect(Collectors.toSet()))
            .failedStep(proto.getFailedStep() != SagaStepType.SAGA_STEP_TYPE_UNSPECIFIED ?
                stepFromProto(proto.getFailedStep()) : null)
            .errorMessage(proto.getErrorMessage().isEmpty() ? null : proto.getErrorMessage())
            .version(proto.getVersion())
            .retryCount(proto.getRetryCount())
            .maxRetries(proto.getMaxRetries())
            .preferences(proto.getPreferences().isEmpty() ? null : proto.getPreferences())
            .createdAt(proto.hasCreatedAt() ?
                Instant.ofEpochSecond(proto.getCreatedAt().getSeconds(), proto.getCreatedAt().getNanos()) : null)
            .updatedAt(proto.hasUpdatedAt() ?
                Instant.ofEpochSecond(proto.getUpdatedAt().getSeconds(), proto.getUpdatedAt().getNanos()) : null)
            .build();
    }

    private String stateFromProto(SagaState state) {
        return switch (state) {
            case SAGA_STATE_INITIAL -> "INITIAL";
            case SAGA_STATE_GEOCODING -> "GEOCODING";
            case SAGA_STATE_ROUTING -> "ROUTING";
            case SAGA_STATE_AI_ENRICHMENT -> "AI_ENRICHMENT";
            case SAGA_STATE_POI_DISCOVERY -> "POI_DISCOVERY";
            case SAGA_STATE_COMPLETED -> "COMPLETED";
            case SAGA_STATE_FAILED -> "FAILED";
            case SAGA_STATE_COMPENSATING -> "COMPENSATING";
            default -> "UNKNOWN";
        };
    }

    private SagaState stateToProto(String state) {
        return switch (state) {
            case "INITIAL" -> SagaState.SAGA_STATE_INITIAL;
            case "GEOCODING" -> SagaState.SAGA_STATE_GEOCODING;
            case "ROUTING" -> SagaState.SAGA_STATE_ROUTING;
            case "AI_ENRICHMENT" -> SagaState.SAGA_STATE_AI_ENRICHMENT;
            case "POI_DISCOVERY" -> SagaState.SAGA_STATE_POI_DISCOVERY;
            case "COMPLETED" -> SagaState.SAGA_STATE_COMPLETED;
            case "FAILED" -> SagaState.SAGA_STATE_FAILED;
            case "COMPENSATING" -> SagaState.SAGA_STATE_COMPENSATING;
            default -> SagaState.SAGA_STATE_UNSPECIFIED;
        };
    }

    private String stepFromProto(SagaStepType step) {
        return switch (step) {
            case SAGA_STEP_TYPE_GEOCODING -> "GEOCODING";
            case SAGA_STEP_TYPE_ROUTING -> "ROUTING";
            case SAGA_STEP_TYPE_AI_ENRICHMENT -> "AI_ENRICHMENT";
            case SAGA_STEP_TYPE_POI_DISCOVERY -> "POI_DISCOVERY";
            default -> "UNKNOWN";
        };
    }
}
