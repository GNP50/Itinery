package com.travel.user.impl.adapter.outbound.grpc;

import com.travel.user.api.port.outbound.UserPersistencePort;
import com.travel.user.grpc.v1.GetUserRequest;
import com.travel.user.grpc.v1.ListUsersRequest;
import com.travel.user.grpc.v1.UserResponse;
import com.travel.user.grpc.v1.UserServiceGrpc;
import com.travel.user.impl.domain.User;
import com.travel.user.impl.domain.UserType;
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
 * gRPC client adapter that implements UserPersistencePort by calling
 * the remote User microservice via gRPC.
 * <p>
 * This adapter bridges the hexagonal architecture port with gRPC remote calls,
 * keeping the core business logic independent of the communication technology.
 * <p>
 * Note: This adapter is READ-ONLY. The save() method throws an exception
 * because user creation/modification should be done via the Auth service endpoints,
 * not through direct persistence operations from other services.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserPersistenceGrpcAdapter implements UserPersistencePort<User> {

    @GrpcClient("user-service")
    private UserServiceGrpc.UserServiceBlockingStub userServiceStub;

    /**
     * Not supported via gRPC client - users should be created/modified
     * via Auth service REST/gRPC endpoints.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public User save(User user) {
        throw new UnsupportedOperationException(
            "User persistence via gRPC client is not supported. " +
            "Use Auth service endpoints for user creation/modification."
        );
    }

    @Override
    public Optional<User> findById(UUID id) {
        log.debug("gRPC findById: {}", id);
        try {
            GetUserRequest request = GetUserRequest.newBuilder()
                .setId(id.toString())
                .build();
            
            UserResponse response = userServiceStub.getUserById(request);
            return Optional.of(toEntity(response));
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                log.debug("User not found by id: {}", id);
                return Optional.empty();
            }
            log.error("gRPC error finding user by id: {}", id, e);
            throw new RuntimeException("Failed to find user by id via gRPC", e);
        }
    }

    @Override
    public Optional<User> findByEmail(String email) {
        log.debug("gRPC findByEmail: {}", email);
        // Use findBySub since email is the sub for registered users
        return findBySub(email);
    }

    @Override
    public Optional<User> findBySub(String sub) {
        log.debug("gRPC findBySub: {}", sub);
        try {
            GetUserRequest request = GetUserRequest.newBuilder()
                .setId(sub)
                .build();
            
            UserResponse response = userServiceStub.getUserBySub(request);
            return Optional.of(toEntity(response));
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                log.debug("User not found by sub: {}", sub);
                return Optional.empty();
            }
            log.error("gRPC error finding user by sub: {}", sub, e);
            throw new RuntimeException("Failed to find user by sub via gRPC", e);
        }
    }

    @Override
    public List<User> findAll(int offset, int limit) {
        log.debug("gRPC findAll: offset={}, limit={}", offset, limit);
        try {
            ListUsersRequest request = ListUsersRequest.newBuilder()
                .setOffset(offset)
                .setLimit(limit)
                .build();
            
            return userServiceStub.listAllUsers(request)
                .getUsersList()
                .stream()
                .map(this::toEntity)
                .collect(Collectors.toList());
        } catch (StatusRuntimeException e) {
            log.error("gRPC error listing users: offset={}, limit={}", offset, limit, e);
            throw new RuntimeException("Failed to list users via gRPC", e);
        }
    }

    @Override
    public long countAll() {
        log.debug("gRPC countAll");
        try {
            ListUsersRequest request = ListUsersRequest.newBuilder()
                .setOffset(0)
                .setLimit(1) // We only need the total count
                .build();
            
            return userServiceStub.listAllUsers(request).getTotal();
        } catch (StatusRuntimeException e) {
            log.error("gRPC error counting users", e);
            throw new RuntimeException("Failed to count users via gRPC", e);
        }
    }

    // -------------------------------------------------------------------------
    // Mapper: gRPC UserResponse → Domain Entity
    // -------------------------------------------------------------------------

    private User toEntity(UserResponse proto) {
        User user = new User();
        user.setId(UUID.fromString(proto.getId()));
        user.setSub(proto.getSub());
        user.setEmail(proto.getEmail());
        user.setName(proto.getName());
        user.setUserType(mapUserType(proto.getUserType()));
        
        if (proto.hasCreatedAt()) {
            user.setCreatedAt(Instant.ofEpochSecond(
                proto.getCreatedAt().getSeconds(),
                proto.getCreatedAt().getNanos()
            ));
        }
        
        return user;
    }

    private UserType mapUserType(com.travel.user.grpc.v1.UserType protoType) {
        return switch (protoType) {
            case USER_TYPE_REGISTERED -> UserType.REGISTERED;
            case USER_TYPE_ANONYMOUS -> UserType.ANONYMOUS;
            case USER_TYPE_ADMIN -> UserType.ADMIN;
            default -> UserType.ANONYMOUS;
        };
    }
}

