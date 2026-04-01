package com.travel.config.rest;

import com.travel.config.domain.ConfigEntry;
import com.travel.config.service.ConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * REST controller exposing CRUD operations for configuration entries.
 * <p>
 * Base path: {@code /api/v1/config}
 * <p>
 * All path variables follow the convention:
 * <ul>
 *   <li>{@code serviceId}  – logical identifier of the owning service</li>
 *   <li>{@code namespace}  – logical sub-group within the service
 *                            (e.g. {@code "default"}, {@code "feature-flags"})</li>
 *   <li>{@code key}        – the individual configuration key</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Config Manager", description = "Centralised configuration store REST API")
public class ConfigRestController {

    private final ConfigService configService;

    // -------------------------------------------------------------------------
    // GET /{serviceId}/{namespace}/{key}
    // -------------------------------------------------------------------------

    @GetMapping("/{serviceId}/{namespace}/{key}")
    @Operation(
            summary = "Get a single configuration entry",
            description = "Retrieve the configuration entry identified by the fully-qualified (serviceId, namespace, key) triplet.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entry found",
                    content = @Content(schema = @Schema(implementation = ConfigEntry.class))),
            @ApiResponse(responseCode = "404", description = "Entry not found",
                    content = @Content)
    })
    public ResponseEntity<ConfigEntry> getConfig(
            @Parameter(description = "Owning service identifier", required = true)
            @PathVariable String serviceId,

            @Parameter(description = "Logical namespace within the service", required = true)
            @PathVariable String namespace,

            @Parameter(description = "Configuration key", required = true)
            @PathVariable String key) {

        log.debug("REST getConfig: serviceId={}, namespace={}, key={}", serviceId, namespace, key);
        return configService.getConfig(serviceId, namespace, key)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // -------------------------------------------------------------------------
    // GET /{serviceId}/{namespace}
    // -------------------------------------------------------------------------

    @GetMapping("/{serviceId}/{namespace}")
    @Operation(
            summary = "List all configuration entries in a namespace",
            description = "Retrieve all configuration entries belonging to the given service and namespace.")
    @ApiResponse(responseCode = "200", description = "List of entries (may be empty)")
    public ResponseEntity<List<ConfigEntry>> getAllConfigs(
            @Parameter(description = "Owning service identifier", required = true)
            @PathVariable String serviceId,

            @Parameter(description = "Logical namespace within the service", required = true)
            @PathVariable String namespace) {

        log.debug("REST getAllConfigs: serviceId={}, namespace={}", serviceId, namespace);
        List<ConfigEntry> entries = configService.getAllConfigs(serviceId, namespace);
        return ResponseEntity.ok(entries);
    }

    // -------------------------------------------------------------------------
    // PUT /{serviceId}/{namespace}/{key}
    // -------------------------------------------------------------------------

    /**
     * Request body for PUT operations. Using a simple record keeps the API
     * surface minimal without requiring a separate DTO class.
     *
     * @param value the configuration value to store
     */
    @Schema(description = "Configuration value payload")
    public record SetConfigRequest(
            @Schema(description = "The configuration value to persist", example = "true")
            String value
    ) {}

    @PutMapping("/{serviceId}/{namespace}/{key}")
    @Operation(
            summary = "Create or update a configuration entry",
            description = "Upsert semantics: creates a new entry if none exists, otherwise updates the existing one.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Entry updated"),
            @ApiResponse(responseCode = "201", description = "Entry created")
    })
    public ResponseEntity<ConfigEntry> setConfig(
            @Parameter(description = "Owning service identifier", required = true)
            @PathVariable String serviceId,

            @Parameter(description = "Logical namespace within the service", required = true)
            @PathVariable String namespace,

            @Parameter(description = "Configuration key", required = true)
            @PathVariable String key,

            @RequestBody SetConfigRequest body) {

        log.info("REST setConfig: serviceId={}, namespace={}, key={}", serviceId, namespace, key);

        boolean existed = configService.getConfig(serviceId, namespace, key).isPresent();
        ConfigEntry saved = configService.setConfig(serviceId, namespace, key, body.value());

        if (existed) {
            return ResponseEntity.ok(saved);
        } else {
            URI location = URI.create("/api/v1/config/%s/%s/%s"
                    .formatted(serviceId, namespace, key));
            return ResponseEntity.created(location).body(saved);
        }
    }

    // -------------------------------------------------------------------------
    // DELETE /{serviceId}/{namespace}/{key}
    // -------------------------------------------------------------------------

    @DeleteMapping("/{serviceId}/{namespace}/{key}")
    @Operation(
            summary = "Delete a configuration entry",
            description = "Permanently removes the configuration entry identified by the given triplet.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Entry deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Entry not found",
                    content = @Content)
    })
    public ResponseEntity<Void> deleteConfig(
            @Parameter(description = "Owning service identifier", required = true)
            @PathVariable String serviceId,

            @Parameter(description = "Logical namespace within the service", required = true)
            @PathVariable String namespace,

            @Parameter(description = "Configuration key", required = true)
            @PathVariable String key) {

        log.info("REST deleteConfig: serviceId={}, namespace={}, key={}", serviceId, namespace, key);
        try {
            configService.deleteConfig(serviceId, namespace, key);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        }
    }
}
