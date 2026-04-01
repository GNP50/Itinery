package com.travel.user.api.port.outbound;

import com.travel.user.api.dto.UserDTO;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound SPI port for User persistence.
 * Implemented by the JPA adapter in user-impl.
 */
public interface UserRepositoryPort {

    Optional<UserDTO.UserResponse> findBySub(String sub);

    Optional<UserDTO.UserResponse> findById(UUID id);

    List<UserDTO.UserResponse> findAll(int offset, int limit);

    long countAll();
}
