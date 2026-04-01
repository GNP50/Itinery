package com.travel.user.impl.service;

import com.travel.user.api.dto.UserDTO;
import com.travel.user.api.port.inbound.UserUseCase;
import com.travel.user.api.port.outbound.UserPersistencePort;
import com.travel.user.impl.domain.User;
import com.travel.user.impl.domain.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService implements UserUseCase {

    private final UserPersistencePort<User> userRepository;

    @Override
    public Optional<UserDTO.UserResponse> findBySub(String sub) {
        log.debug("Finding user by sub: {}", sub);
        return userRepository.findBySub(sub)
            .map(this::toDto);
    }

    @Override
    public Optional<UserDTO.UserResponse> findById(UUID id) {
        log.debug("Finding user by id: {}", id);
        return userRepository.findById(id)
            .map(this::toDto);
    }

    @Override
    public UserDTO.PagedUsersResponse listUsers(int offset, int limit) {
        log.debug("Listing users with offset={} limit={}", offset, limit);
        int pageSize = limit > 0 ? limit : 20;
        
        List<UserDTO.UserResponse> users = userRepository.findAll(offset, pageSize)
            .stream()
            .map(this::toDto)
            .toList();
        
        long total = userRepository.countAll();
        
        return UserDTO.PagedUsersResponse.builder()
            .users(users)
            .total(total)
            .offset(offset)
            .limit(pageSize)
            .build();
    }

    @Override
    public boolean isAdmin(UUID userId) {
        log.debug("Checking admin status for userId: {}", userId);
        return userRepository.findById(userId)
            .map(user -> user.getUserType() == UserType.REGISTERED || user.getUserType() == UserType.ADMIN)
            .orElse(false);
    }

    // -------------------------------------------------------------------------
    // Mapper
    // -------------------------------------------------------------------------

    private UserDTO.UserResponse toDto(User user) {
        return UserDTO.UserResponse.builder()
            .id(user.getId())
            .sub(user.getSub())
            .email(user.getEmail())
            .name(user.getName())
            .userType(user.getUserType() != null ? user.getUserType().name() : null)
            .createdAt(user.getCreatedAt())
            .build();
    }
}
