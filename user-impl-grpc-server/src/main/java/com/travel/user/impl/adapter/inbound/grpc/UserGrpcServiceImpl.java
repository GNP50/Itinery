package com.travel.user.impl.adapter.inbound.grpc;

import com.google.protobuf.Timestamp;
import com.travel.user.api.dto.UserDTO;
import com.travel.user.api.port.inbound.UserUseCase;
import com.travel.user.grpc.v1.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.UUID;

/**
 * gRPC server implementation for the UserService.
 * Exposes user lookup, listing and admin-status RPCs.
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class UserGrpcServiceImpl extends UserServiceGrpc.UserServiceImplBase {

    private final UserUseCase userUseCase;

    // -------------------------------------------------------------------------
    // GetUserBySub
    // -------------------------------------------------------------------------

    @Override
    public void getUserBySub(GetUserRequest request,
                             StreamObserver<UserResponse> responseObserver) {
        log.debug("gRPC GetUserBySub sub={}", request.getId());
        userUseCase.findBySub(request.getId())
            .ifPresentOrElse(
                user -> {
                    responseObserver.onNext(toProto(user));
                    responseObserver.onCompleted();
                },
                () -> responseObserver.onError(
                    Status.NOT_FOUND
                        .withDescription("User not found for sub: " + request.getId())
                        .asRuntimeException()
                )
            );
    }

    // -------------------------------------------------------------------------
    // GetUserById
    // -------------------------------------------------------------------------

    @Override
    public void getUserById(GetUserRequest request,
                            StreamObserver<UserResponse> responseObserver) {
        log.debug("gRPC GetUserById id={}", request.getId());
        UUID id;
        try {
            id = UUID.fromString(request.getId());
        } catch (IllegalArgumentException e) {
            responseObserver.onError(
                Status.INVALID_ARGUMENT
                    .withDescription("Invalid UUID: " + request.getId())
                    .asRuntimeException()
            );
            return;
        }

        userUseCase.findById(id)
            .ifPresentOrElse(
                user -> {
                    responseObserver.onNext(toProto(user));
                    responseObserver.onCompleted();
                },
                () -> responseObserver.onError(
                    Status.NOT_FOUND
                        .withDescription("User not found for id: " + request.getId())
                        .asRuntimeException()
                )
            );
    }

    // -------------------------------------------------------------------------
    // ListAllUsers
    // -------------------------------------------------------------------------

    @Override
    public void listAllUsers(ListUsersRequest request,
                             StreamObserver<ListUsersResponse> responseObserver) {
        log.debug("gRPC ListAllUsers offset={} limit={}", request.getOffset(), request.getLimit());
        int limit = request.getLimit() > 0 ? request.getLimit() : 20;

        UserDTO.PagedUsersResponse paged = userUseCase.listUsers(request.getOffset(), limit);

        ListUsersResponse.Builder builder = ListUsersResponse.newBuilder()
            .setTotal(paged.total());

        paged.users().forEach(u -> builder.addUsers(toProto(u)));

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    // -------------------------------------------------------------------------
    // CheckAdminStatus
    // -------------------------------------------------------------------------

    @Override
    public void checkAdminStatus(CheckAdminRequest request,
                                 StreamObserver<AdminStatusResponse> responseObserver) {
        log.debug("gRPC CheckAdminStatus userId={}", request.getUserId());
        UUID userId;
        try {
            userId = UUID.fromString(request.getUserId());
        } catch (IllegalArgumentException e) {
            responseObserver.onError(
                Status.INVALID_ARGUMENT
                    .withDescription("Invalid UUID: " + request.getUserId())
                    .asRuntimeException()
            );
            return;
        }

        boolean isAdmin = userUseCase.isAdmin(userId);
        responseObserver.onNext(AdminStatusResponse.newBuilder().setIsAdmin(isAdmin).build());
        responseObserver.onCompleted();
    }

    // -------------------------------------------------------------------------
    // Mapper
    // -------------------------------------------------------------------------

    private UserResponse toProto(UserDTO.UserResponse dto) {
        UserResponse.Builder builder = UserResponse.newBuilder()
            .setId(dto.id() != null ? dto.id().toString() : "")
            .setSub(dto.sub() != null ? dto.sub() : "")
            .setEmail(dto.email() != null ? dto.email() : "")
            .setName(dto.name() != null ? dto.name() : "")
            .setUserType(mapUserType(dto.userType()));

        if (dto.createdAt() != null) {
            builder.setCreatedAt(Timestamp.newBuilder()
                .setSeconds(dto.createdAt().getEpochSecond())
                .setNanos(dto.createdAt().getNano())
                .build());
        }

        return builder.build();
    }

    private UserType mapUserType(String type) {
        if ("REGISTERED".equals(type)) return UserType.USER_TYPE_REGISTERED;
        if ("ANONYMOUS".equals(type))  return UserType.USER_TYPE_ANONYMOUS;
        if ("ADMIN".equals(type))      return UserType.USER_TYPE_ADMIN;
        return UserType.USER_TYPE_UNSPECIFIED;
    }
}
