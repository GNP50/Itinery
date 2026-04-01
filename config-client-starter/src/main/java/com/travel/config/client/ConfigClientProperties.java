package com.travel.config.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Externalized configuration properties for the gRPC-based config client.
 *
 * <p>Bind via {@code application.yml}:
 * <pre>
 * config:
 *   client:
 *     enabled: true
 *     server-host: config-manager
 *     server-port: 9090
 *     service-name: my-service
 *     default-namespace: production
 *     cache-ttl-seconds: 60
 * </pre>
 */
@Validated
@ConfigurationProperties(prefix = "config.client")
public class ConfigClientProperties {

    /**
     * Toggle for the entire auto-configuration. Set to {@code false} to disable
     * the config client completely (e.g. in local development).
     */
    private boolean enabled = true;

    /**
     * Hostname or IP address of the config-manager gRPC server.
     */
    @NotBlank
    private String serverHost = "localhost";

    /**
     * Port on which the config-manager gRPC server listens.
     */
    @Min(1)
    @Max(65535)
    private int serverPort = 9090;

    /**
     * Logical name of this service – used as the {@code serviceName} selector
     * when querying the config-manager.
     */
    @NotBlank
    private String serviceName = "default";

    /**
     * Default configuration namespace / environment label
     * (e.g. {@code production}, {@code staging}).
     */
    @NotBlank
    private String defaultNamespace = "default";

    /**
     * How long (in seconds) fetched configuration entries are kept in the
     * in-process cache before the next gRPC call is issued.
     * Must be a positive integer.
     */
    @Positive
    private long cacheTtlSeconds = 60;

    // ── Getters & setters ────────────────────────────────────────────────────

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServerHost() {
        return serverHost;
    }

    public void setServerHost(String serverHost) {
        this.serverHost = serverHost;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getDefaultNamespace() {
        return defaultNamespace;
    }

    public void setDefaultNamespace(String defaultNamespace) {
        this.defaultNamespace = defaultNamespace;
    }

    public long getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    public void setCacheTtlSeconds(long cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    @Override
    public String toString() {
        return "ConfigClientProperties{"
                + "enabled=" + enabled
                + ", serverHost='" + serverHost + '\''
                + ", serverPort=" + serverPort
                + ", serviceName='" + serviceName + '\''
                + ", defaultNamespace='" + defaultNamespace + '\''
                + ", cacheTtlSeconds=" + cacheTtlSeconds
                + '}';
    }
}
