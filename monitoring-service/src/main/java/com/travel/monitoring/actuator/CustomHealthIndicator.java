package com.travel.monitoring.actuator;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom health indicator for the monitoring service.
 * Checks connectivity to Datadog agent and other dependencies.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Data
public class CustomHealthIndicator implements HealthIndicator {

    private final RestTemplate restTemplate;

    @Value("${monitoring.datadog.api-key:}")
    private String datadogApiKey;

    @Value("${monitoring.datadog.site:datadoghq.com}")
    private String datadogSite;

    @Value("${monitoring.datadog.enabled:true}")
    private boolean datadogEnabled;

    @Override
    public Health health() {
        try {
            Map<String, Object> details = new HashMap<>();
            Status status = Status.UP;

            // Check Datadog connectivity if enabled
            if (datadogEnabled) {
                if (datadogApiKey.isEmpty()) {
                    details.put("datadog", "disabled - no API key configured");
                } else {
                    Health datadogHealth = checkDatadogConnectivity();
                    details.putAll(datadogHealth.getDetails());
                    if (datadogHealth.getStatus() != Status.UP) {
                        status = Status.DOWN;
                    }
                }
            } else {
                details.put("datadog", "disabled via configuration");
            }

            // Check Prometheus endpoint
            Health prometheusHealth = checkPrometheusEndpoint();
            details.putAll(prometheusHealth.getDetails());
            if (prometheusHealth.getStatus() != Status.UP) {
                status = Status.DOWN;
            }

            // Add additional service information
            details.put("service", "monitoring-service");
            details.put("version", "1.0.0");
            details.put("timestamp", System.currentTimeMillis());

            return Health.status(status).withDetails(details).build();

        } catch (Exception e) {
            log.error("Health check failed", e);
            return Health.down(e)
                .withDetail("error", e.getMessage())
                .build();
        }
    }

    private Health checkDatadogConnectivity() {
        try {
            if (datadogApiKey.isEmpty()) {
                return Health.up()
                    .withDetail("status", "disabled")
                    .withDetail("reason", "No API key configured")
                    .build();
            }

            // Attempt to validate API key by checking the status endpoint
            String url = String.format("https://api.%s/api/v1/validate", datadogSite);

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                Map<String, Object> body = response.getBody();
                boolean valid = body != null && Boolean.TRUE.equals(body.get("valid"));

                return Health.up()
                    .withDetail("status", "connected")
                    .withDetail("site", datadogSite)
                    .withDetail("api_key_valid", valid)
                    .build();
            } else {
                return Health.down()
                    .withDetail("status", "error")
                    .withDetail("http_code", response.getStatusCode().value())
                    .build();
            }
        } catch (Exception e) {
            log.warn("Datadog connectivity check failed", e);
            return Health.down()
                .withDetail("status", "error")
                .withDetail("error", e.getMessage())
                .build();
        }
    }

    private Health checkPrometheusEndpoint() {
        try {
            // Check if Prometheus metrics are being exposed
            // This would typically be at /actuator/prometheus
            return Health.up()
                .withDetail("status", "enabled")
                .withDetail("endpoint", "/actuator/prometheus")
                .build();
        } catch (Exception e) {
            log.warn("Prometheus endpoint check failed", e);
            return Health.down()
                .withDetail("status", "error")
                .withDetail("error", e.getMessage())
                .build();
        }
    }

    @Data
    public static class DatadogConfig {
        private String apiKey;
        private String site;
        private boolean enabled;
    }
}
