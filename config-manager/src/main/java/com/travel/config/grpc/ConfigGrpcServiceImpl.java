package com.travel.config.grpc;

import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import com.travel.config.domain.ConfigEntry;
import com.travel.config.grpc.v1.ConfigServiceGrpc;
import com.travel.config.grpc.v1.DeleteConfigRequest;
import com.travel.config.grpc.v1.GetConfigRequest;
import com.travel.config.grpc.v1.GetConfigResponse;
import com.travel.config.grpc.v1.ListConfigsRequest;
import com.travel.config.grpc.v1.ListConfigsResponse;
import com.travel.config.grpc.v1.SetConfigRequest;
import com.travel.config.grpc.v1.SetConfigResponse;
import com.travel.config.service.ConfigService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * gRPC service implementation for the {@code ConfigService} RPC contract
 * defined in {@code config-service.proto}.
 * <p>
 * Field mapping between proto and the JPA entity:
 * <ul>
 *   <li>{@code application} in proto &rarr; {@code serviceId} in entity</li>
 *   <li>{@code environment} in proto &rarr; {@code namespace} in entity</li>
 *   <li>{@code key}         in proto &rarr; {@code configKey} in entity</li>
 *   <li>{@code value}       in proto &rarr; {@code configValue} in entity</li>
 * </ul>
 * The {@code WatchConfig} streaming RPC is stubbed and returns
 * {@code UNIMPLEMENTED} – reactive push notifications require an additional
 * event-bus integration outside the scope of this module.
 */
@GrpcService
@RequiredArgsConstructor
@Slf4j
public class ConfigGrpcServiceImpl extends ConfigServiceGrpc.ConfigServiceImplBase {

    private final ConfigService configService;

    // -------------------------------------------------------------------------
    // GetConfig
    // -------------------------------------------------------------------------

    @Override
    public void getConfig(GetConfigRequest request,
                          StreamObserver<GetConfigResponse> responseObserver) {
        log.debug("gRPC getConfig: application={}, environment={}, key={}",
                request.getApplication(), request.getEnvironment(), request.getKey());
        try {
            configService.getConfig(
                            request.getApplication(),
                            request.getEnvironment(),
                            request.getKey())
                    .ifPresentOrElse(
                            entry -> {
                                GetConfigResponse response = GetConfigResponse.newBuilder()
                                        .setEntry(toProto(entry))
                                        .build();
                                responseObserver.onNext(response);
                                responseObserver.onCompleted();
                            },
                            () -> responseObserver.onError(
                                    Status.NOT_FOUND
                                            .withDescription("Config entry not found: application=%s, environment=%s, key=%s"
                                                    .formatted(request.getApplication(),
                                                               request.getEnvironment(),
                                                               request.getKey()))
                                            .asRuntimeException())
                    );
        } catch (Exception ex) {
            log.error("gRPC getConfig error", ex);
            responseObserver.onError(
                    Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    // -------------------------------------------------------------------------
    // ListConfigs
    // -------------------------------------------------------------------------

    @Override
    public void listConfigs(ListConfigsRequest request,
                            StreamObserver<ListConfigsResponse> responseObserver) {
        log.debug("gRPC listConfigs: application={}, environment={}",
                request.getApplication(), request.getEnvironment());
        try {
            List<ConfigEntry> entries = configService.getAllConfigs(
                    request.getApplication(), request.getEnvironment());

            // Apply optional key-prefix filter client-side (simple starts-with)
            String keyPrefix = request.getKeyPrefix();
            if (keyPrefix != null && !keyPrefix.isBlank()) {
                entries = entries.stream()
                        .filter(e -> e.getConfigKey().startsWith(keyPrefix))
                        .toList();
            }

            ListConfigsResponse.Builder builder = ListConfigsResponse.newBuilder()
                    .setTotalCount(entries.size());
            entries.forEach(e -> builder.addEntries(toProto(e)));

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("gRPC listConfigs error", ex);
            responseObserver.onError(
                    Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    // -------------------------------------------------------------------------
    // SetConfig
    // -------------------------------------------------------------------------

    @Override
    public void setConfig(SetConfigRequest request,
                          StreamObserver<SetConfigResponse> responseObserver) {
        log.info("gRPC setConfig: application={}, environment={}, key={}",
                request.getApplication(), request.getEnvironment(), request.getKey());
        try {
            ConfigEntry saved = configService.setConfig(
                    request.getApplication(),
                    request.getEnvironment(),
                    request.getKey(),
                    request.getValue());

            SetConfigResponse response = SetConfigResponse.newBuilder()
                    .setEntry(toProto(saved))
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("gRPC setConfig error", ex);
            responseObserver.onError(
                    Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    // -------------------------------------------------------------------------
    // DeleteConfig
    // -------------------------------------------------------------------------

    @Override
    public void deleteConfig(DeleteConfigRequest request,
                             StreamObserver<Empty> responseObserver) {
        log.info("gRPC deleteConfig: application={}, environment={}, key={}",
                request.getApplication(), request.getEnvironment(), request.getKey());
        try {
            configService.deleteConfig(
                    request.getApplication(),
                    request.getEnvironment(),
                    request.getKey());
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (NoSuchElementException ex) {
            responseObserver.onError(
                    Status.NOT_FOUND.withDescription(ex.getMessage()).asRuntimeException());
        } catch (Exception ex) {
            log.error("gRPC deleteConfig error", ex);
            responseObserver.onError(
                    Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Convert a JPA {@link ConfigEntry} entity to the proto {@code ConfigEntry}
     * message type.
     */
    private com.travel.config.grpc.v1.ConfigEntry toProto(ConfigEntry entity) {
        com.travel.config.grpc.v1.ConfigEntry.Builder builder =
                com.travel.config.grpc.v1.ConfigEntry.newBuilder()
                        .setId(entity.getId() != null ? entity.getId().toString() : "")
                        .setApplication(entity.getServiceId())
                        .setEnvironment(entity.getNamespace())
                        .setKey(entity.getConfigKey())
                        .setValue(entity.getConfigValue() != null ? entity.getConfigValue() : "")
                        .setVersion(entity.getVersion())
                        .setEncrypted(entity.isEncrypted());

        if (entity.getUpdatedAt() != null) {
            builder.setUpdatedAt(toTimestamp(entity.getUpdatedAt()));
        }

        return builder.build();
    }

    /** Convert {@link OffsetDateTime} to a protobuf {@link Timestamp}. */
    private static Timestamp toTimestamp(OffsetDateTime odt) {
        return Timestamp.newBuilder()
                .setSeconds(odt.toEpochSecond())
                .setNanos(odt.getNano())
                .build();
    }
}
