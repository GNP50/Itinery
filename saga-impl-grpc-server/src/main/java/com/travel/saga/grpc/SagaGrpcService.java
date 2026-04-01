package com.travel.saga.grpc;

import com.travel.saga.dto.SagaInstanceDto;
import com.travel.saga.dto.SagaListRequest;
import com.travel.saga.grpc.v1.*;
import com.travel.saga.port.inbound.SagaCommandPort;
import com.travel.saga.port.inbound.SagaQueryPort;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * gRPC service implementation for Saga orchestrator.
 * Exposes saga query and command operations via gRPC.
 */
@GrpcService
@Slf4j
@RequiredArgsConstructor
public class SagaGrpcService extends SagaServiceGrpc.SagaServiceImplBase {

    private final SagaQueryPort queryPort;
    private final SagaCommandPort commandPort;

    @Override
    public void getSagaInstance(GetSagaInstanceRequest request, StreamObserver<SagaInstanceResponse> responseObserver) {
        log.debug("gRPC getSagaInstance called for saga: {}", request.getSagaId());

        try {
            UUID sagaId = UUID.fromString(request.getSagaId());
            var sagaOpt = queryPort.getSagaInstance(sagaId);

            if (sagaOpt.isEmpty()) {
                responseObserver.onError(Status.NOT_FOUND
                    .withDescription("Saga not found: " + request.getSagaId())
                    .asRuntimeException());
                return;
            }

            var response = toProto(sagaOpt.get());
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID: {}", request.getSagaId(), e);
            responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription("Invalid saga ID format")
                .asRuntimeException());
        } catch (Exception e) {
            log.error("Error getting saga instance", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("Internal error: " + e.getMessage())
                .asRuntimeException());
        }
    }

    @Override
    public void getSagaByItinerary(GetSagaByItineraryRequest request, StreamObserver<GetSagaByItineraryResponse> responseObserver) {
        log.debug("gRPC getSagaByItinerary called for itinerary: {}", request.getItineraryId());

        try {
            UUID itineraryId = UUID.fromString(request.getItineraryId());
            List<SagaInstanceDto> sagas = queryPort.getSagasByItinerary(itineraryId);

            var response = GetSagaByItineraryResponse.newBuilder()
                .addAllSagaInstances(sagas.stream()
                    .map(this::toProto)
                    .collect(Collectors.toList()))
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID: {}", request.getItineraryId(), e);
            responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription("Invalid itinerary ID format")
                .asRuntimeException());
        } catch (Exception e) {
            log.error("Error getting saga by itinerary", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("Internal error: " + e.getMessage())
                .asRuntimeException());
        }
    }

    @Override
    public void listSagaInstances(ListSagaInstancesRequest request, StreamObserver<ListSagaInstancesResponse> responseObserver) {
        log.debug("gRPC listSagaInstances called with offset: {}, limit: {}", request.getOffset(), request.getLimit());

        try {
            var listRequest = SagaListRequest.builder()
                .offset(request.getOffset())
                .limit(request.getLimit() > 0 ? request.getLimit() : 20)
                .stateFilter(request.getStateFilter() != SagaState.SAGA_STATE_UNSPECIFIED ?
                    stateFromProto(request.getStateFilter()) : null)
                .itineraryId(request.getItineraryId().isEmpty() ? null : UUID.fromString(request.getItineraryId()))
                .build();

            List<SagaInstanceDto> sagas = queryPort.listSagaInstances(listRequest);
            long total = queryPort.countSagaInstances(listRequest);

            var response = ListSagaInstancesResponse.newBuilder()
                .addAllSagaInstances(sagas.stream()
                    .map(this::toProto)
                    .collect(Collectors.toList()))
                .setTotal(total)
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error listing saga instances", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("Internal error: " + e.getMessage())
                .asRuntimeException());
        }
    }

    @Override
    public void retrySaga(RetrySagaRequest request, StreamObserver<RetrySagaResponse> responseObserver) {
        log.info("gRPC retrySaga called for saga: {}", request.getSagaId());

        try {
            UUID sagaId = UUID.fromString(request.getSagaId());
            var result = commandPort.retrySaga(sagaId);

            var response = RetrySagaResponse.newBuilder()
                .setSuccess(result.success())
                .setMessage(result.message())
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID: {}", request.getSagaId(), e);
            responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription("Invalid saga ID format")
                .asRuntimeException());
        } catch (Exception e) {
            log.error("Error retrying saga", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("Internal error: " + e.getMessage())
                .asRuntimeException());
        }
    }

    @Override
    public void compensateSaga(CompensateSagaRequest request, StreamObserver<CompensateSagaResponse> responseObserver) {
        log.info("gRPC compensateSaga called for saga: {}", request.getSagaId());

        try {
            UUID sagaId = UUID.fromString(request.getSagaId());
            var result = commandPort.compensateSaga(sagaId);

            var response = CompensateSagaResponse.newBuilder()
                .setSuccess(result.success())
                .setMessage(result.message())
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID: {}", request.getSagaId(), e);
            responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription("Invalid saga ID format")
                .asRuntimeException());
        } catch (Exception e) {
            log.error("Error compensating saga", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("Internal error: " + e.getMessage())
                .asRuntimeException());
        }
    }

    @Override
    public void getSagaHistory(GetSagaHistoryRequest request, StreamObserver<GetSagaHistoryResponse> responseObserver) {
        log.debug("gRPC getSagaHistory called for saga: {}", request.getSagaId());

        try {
            UUID sagaId = UUID.fromString(request.getSagaId());
            var history = queryPort.getSagaHistory(sagaId);

            var response = GetSagaHistoryResponse.newBuilder()
                .setSagaId(request.getSagaId())
                .addAllTransitions(history.stream()
                    .map(t -> SagaStateTransition.newBuilder()
                        .setFromState(t.fromState())
                        .setToState(t.toState())
                        .setEvent(t.event())
                        .setTimestamp(Timestamp.newBuilder()
                            .setSeconds(t.timestamp().getEpochSecond())
                            .setNanos(t.timestamp().getNano())
                            .build())
                        .build())
                    .collect(Collectors.toList()))
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID: {}", request.getSagaId(), e);
            responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription("Invalid saga ID format")
                .asRuntimeException());
        } catch (Exception e) {
            log.error("Error getting saga history", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("Internal error: " + e.getMessage())
                .asRuntimeException());
        }
    }

    private SagaInstanceResponse toProto(SagaInstanceDto dto) {
        var builder = SagaInstanceResponse.newBuilder()
            .setId(dto.id().toString())
            .setItineraryId(dto.itineraryId().toString())
            .setCurrentState(stateToProto(dto.currentState()))
            .setVersion(dto.version())
            .setRetryCount(dto.retryCount())
            .setMaxRetries(dto.maxRetries());

        if (dto.completedSteps() != null) {
            dto.completedSteps().stream()
                .map(this::stepToProto)
                .forEach(builder::addCompletedSteps);
        }

        if (dto.failedStep() != null && !dto.failedStep().isEmpty()) {
            builder.setFailedStep(stepToProto(dto.failedStep()));
        }

        if (dto.errorMessage() != null) {
            builder.setErrorMessage(dto.errorMessage());
        }

        if (dto.preferences() != null) {
            builder.setPreferences(dto.preferences());
        }

        if (dto.createdAt() != null) {
            builder.setCreatedAt(Timestamp.newBuilder()
                .setSeconds(dto.createdAt().getEpochSecond())
                .setNanos(dto.createdAt().getNano())
                .build());
        }

        if (dto.updatedAt() != null) {
            builder.setUpdatedAt(Timestamp.newBuilder()
                .setSeconds(dto.updatedAt().getEpochSecond())
                .setNanos(dto.updatedAt().getNano())
                .build());
        }

        return builder.build();
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
            default -> null;
        };
    }

    private SagaStepType stepToProto(String step) {
        return switch (step) {
            case "GEOCODING" -> SagaStepType.SAGA_STEP_TYPE_GEOCODING;
            case "ROUTING" -> SagaStepType.SAGA_STEP_TYPE_ROUTING;
            case "AI_ENRICHMENT" -> SagaStepType.SAGA_STEP_TYPE_AI_ENRICHMENT;
            case "POI_DISCOVERY" -> SagaStepType.SAGA_STEP_TYPE_POI_DISCOVERY;
            default -> SagaStepType.SAGA_STEP_TYPE_UNSPECIFIED;
        };
    }
}
