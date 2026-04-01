package com.travel.user.api.port.outbound;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound SPI port for User persistence operations.
 * <p>
 * Implementations may delegate to a relational database (JPA), remote service (gRPC),
 * or any other persistence technology. The domain layer depends only on this
 * interface and never on a specific technology.
 *
 * @param <T> the aggregate root type (typically {@code User} domain entity)
 */
public interface UserPersistencePort<T> {

    /**
     * Persist a new user or replace an existing one (upsert semantics).
     *
     * @param user the user to save; must not be {@code null}
     * @return the saved user, potentially with server-assigned fields
     *         (e.g. {@code id}, {@code createdAt}) populated
     */
    T save(T user);

    /**
     * Finds a user by their internal UUID.
     *
     * @param id the user UUID; must not be {@code null}
     * @return optional user if found
     */
    Optional<T> findById(UUID id);

    /**
     * Finds a user by email address.
     *
     * @param email the email to search for; must not be {@code null}
     * @return optional user if found
     */
    Optional<T> findByEmail(String email);

    /**
     * Finds a user by subject identifier (email for registered users, anon_* for anonymous).
     *
     * @param sub the subject identifier; must not be {@code null}
     * @return optional user if found
     */
    Optional<T> findBySub(String sub);

    /**
     * Retrieves all users with pagination.
     *
     * @param offset zero-based row offset
     * @param limit  maximum number of rows to return
     * @return list of users, may be empty
     */
    List<T> findAll(int offset, int limit);

    /**
     * Count total number of users.
     *
     * @return total count
     */
    long countAll();
}

