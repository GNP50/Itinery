package com.travel.config.repository;

import com.travel.config.domain.ConfigEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ConfigEntry} persistence.
 * <p>
 * Query methods follow the Spring Data naming convention so that no manual
 * JPQL/SQL is required for the standard CRUD operations used by
 * {@link com.travel.config.service.ConfigService}.
 */
@Repository
public interface ConfigRepository extends JpaRepository<ConfigEntry, UUID> {

    /**
     * Find a single configuration entry by its fully-qualified key.
     *
     * @param serviceId  the owning service identifier
     * @param namespace  the logical namespace within the service
     * @param configKey  the configuration key
     * @return an {@link Optional} containing the entry if it exists
     */
    Optional<ConfigEntry> findByServiceIdAndNamespaceAndConfigKey(
            String serviceId,
            String namespace,
            String configKey
    );

    /**
     * Find all configuration entries belonging to a specific service namespace.
     * Results are not guaranteed to be sorted; callers should sort as needed.
     *
     * @param serviceId the owning service identifier
     * @param namespace the logical namespace within the service
     * @return a list of matching entries; never {@code null}, may be empty
     */
    List<ConfigEntry> findByServiceIdAndNamespace(String serviceId, String namespace);

    /**
     * Find all configuration entries owned by a specific service across all
     * namespaces.
     *
     * @param serviceId the owning service identifier
     * @return a list of matching entries; never {@code null}, may be empty
     */
    List<ConfigEntry> findByServiceId(String serviceId);
}
