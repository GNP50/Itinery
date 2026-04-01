package com.travel.user.impl.adapter.outbound.persistence;

import com.travel.user.api.port.outbound.UserPersistencePort;
import com.travel.user.impl.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA adapter that implements the UserPersistencePort by delegating to UserJpaRepository.
 * <p>
 * This adapter bridges the hexagonal architecture port with Spring Data JPA,
 * keeping the core business logic independent of persistence technology.
 */
@Component
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserPersistencePort<User> {

    private final UserJpaRepository jpaRepository;

    @Override
    public User save(User user) {
        return jpaRepository.save(user);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email);
    }

    @Override
    public Optional<User> findBySub(String sub) {
        return jpaRepository.findBySub(sub);
    }

    @Override
    public List<User> findAll(int offset, int limit) {
        int pageSize = limit > 0 ? limit : 20;
        int pageNumber = offset / pageSize;
        return jpaRepository.findAll(PageRequest.of(pageNumber, pageSize)).getContent();
    }

    @Override
    public long countAll() {
        return jpaRepository.count();
    }
}
