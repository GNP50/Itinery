package com.travel.config.service;

import com.travel.config.domain.ConfigEntry;
import com.travel.config.repository.ConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Application service for managing configuration entries.
 * <p>
 * All mutating operations are wrapped in a transaction. Read operations use
 * a read-only transaction to benefit from connection-pool optimisations on
 * supporting JDBC drivers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigService {

    private final ConfigRepository configRepository;

    // -------------------------------------------------------------------------
    // Read operations
    // -------------------------------------------------------------------------

    /**
     * Retrieve a single configuration value.
     *
     * @param serviceId the owning service identifier; must not be {@code null}
     * @param namespace the logical namespace; must not be {@code null}
     * @param key       the configuration key; must not be {@code null}
     * @return an {@link Optional} containing the entry if it exists
     */
    @Transactional(readOnly = true)
    public Optional<ConfigEntry> getConfig(String serviceId, String namespace, String key) {
        log.debug("getConfig: serviceId={}, namespace={}, key={}", serviceId, namespace, key);
        return configRepository.findByServiceIdAndNamespaceAndConfigKey(serviceId, namespace, key);
    }

    /**
     * Retrieve all configuration entries in a given service namespace.
     *
     * @param serviceId the owning service identifier; must not be {@code null}
     * @param namespace the logical namespace; must not be {@code null}
     * @return an unmodifiable list of entries; never {@code null}, may be empty
     */
    @Transactional(readOnly = true)
    public List<ConfigEntry> getAllConfigs(String serviceId, String namespace) {
        log.debug("getAllConfigs: serviceId={}, namespace={}", serviceId, namespace);
        return List.copyOf(configRepository.findByServiceIdAndNamespace(serviceId, namespace));
    }

    // -------------------------------------------------------------------------
    // Write operations
    // -------------------------------------------------------------------------

    /**
     * Create or update a configuration entry (upsert semantics).
     * <p>
     * If an entry for the given {@code (serviceId, namespace, key)} triplet already
     * exists its {@code configValue} is updated in-place (triggering the
     * optimistic-lock version increment). Otherwise a new entry is created.
     *
     * @param serviceId the owning service identifier; must not be {@code null}
     * @param namespace the logical namespace; must not be {@code null}
     * @param key       the configuration key; must not be {@code null}
     * @param value     the value to store; {@code null} is a valid value meaning
     *                  "explicitly unset"
     * @return the persisted (and potentially updated) {@link ConfigEntry};
     *         never {@code null}
     */
    @Transactional
    public ConfigEntry setConfig(String serviceId, String namespace, String key, String value) {
        log.info("setConfig: serviceId={}, namespace={}, key={}", serviceId, namespace, key);

        ConfigEntry entry = configRepository
                .findByServiceIdAndNamespaceAndConfigKey(serviceId, namespace, key)
                .orElseGet(() -> ConfigEntry.builder()
                        .serviceId(serviceId)
                        .namespace(namespace)
                        .configKey(key)
                        .build());

        entry.setConfigValue(value);
        return configRepository.save(entry);
    }

    /**
     * Delete a configuration entry identified by its fully-qualified key.
     *
     * @param serviceId the owning service identifier; must not be {@code null}
     * @param namespace the logical namespace; must not be {@code null}
     * @param key       the configuration key; must not be {@code null}
     * @throws NoSuchElementException if no entry exists for the given triplet
     */
    @Transactional
    public void deleteConfig(String serviceId, String namespace, String key) {
        log.info("deleteConfig: serviceId={}, namespace={}, key={}", serviceId, namespace, key);

        ConfigEntry entry = configRepository
                .findByServiceIdAndNamespaceAndConfigKey(serviceId, namespace, key)
                .orElseThrow(() -> new NoSuchElementException(
                        "Config entry not found: serviceId=%s, namespace=%s, key=%s"
                                .formatted(serviceId, namespace, key)));

        configRepository.delete(entry);
    }
}
