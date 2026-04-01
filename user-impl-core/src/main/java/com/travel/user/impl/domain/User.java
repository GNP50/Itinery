package com.travel.user.impl.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing an application user.
 * Maps to the shared {@code users} table managed by the monolith's Flyway migrations.
 */
@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_users_sub",   columnList = "sub",   unique = true),
        @Index(name = "idx_users_email", columnList = "email")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "sub", nullable = false, unique = true, length = 255)
    private String sub;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 20)
    private UserType userType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onPrePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Creates a new registered user with email authentication.
     *
     * @param email        unique email address (also used as subject)
     * @param name         display name
     * @param passwordHash BCrypt-hashed password
     * @return new User instance
     */
    public static User createRegistered(String email, String name, String passwordHash) {
        User user = new User();
        user.setSub(email);
        user.setEmail(email);
        user.setName(name);
        user.setPasswordHash(passwordHash);
        user.setUserType(UserType.REGISTERED);
        return user;
    }

    /**
     * Creates a new anonymous user with a randomly generated subject.
     *
     * @return new User instance
     */
    public static User createAnonymous() {
        User user = new User();
        user.setSub("anon_" + UUID.randomUUID());
        user.setUserType(UserType.ANONYMOUS);
        return user;
    }
}
