package com.travel.user.impl.adapter.outbound.persistence;

import com.travel.user.impl.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<User, UUID> {

    Optional<User> findBySub(String sub);

    Optional<User> findByEmail(String email);

    Page<User> findAll(Pageable pageable);
}
