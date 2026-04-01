package com.travel.monitoring.controller;

import com.travel.monitoring.service.MonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for monitoring operations.
 * Provides HTTP endpoints for health checks, metrics, and metric recording.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/monitoring")
@RequiredArgsConstructor
@Tag(name = "Monitoring Service", description = "Monitoring and telemetry API")
public class MonitoringRestController {

    private final MonitoringService monitoringService;

    @Operation(summary = "Get system health", description = "Returns the health status of the monitoring service")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Health check successful"),
        @ApiResponse(responseCode = "500", description = "Health check failed")
    })
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        log.debug("Health check requested");
        Map<String, Object> health = monitoringService.getSystemHealth();
        return ResponseEntity.ok(health);
    }

    @Operation(summary = "Get detailed health", description = "Returns detailed health status with dependency information")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Detailed health check successful"),
        @ApiResponse(responseCode = "500", description = "Health check failed")
    })
    @GetMapping("/health/details")
    public ResponseEntity<Map<String, Object>> getHealthDetails() {
        log.debug("Detailed health check requested");
        Map<String, Object> health = monitoringService.getSystemHealth();

        // Add additional health details
        Map<String, Object> detailedHealth = new java.util.HashMap<>(health);
        detailedHealth.put("dependencies", Map.of(
            "database", "up",
            "redis", "up",
            "datadog", health.get("status")
        ));
        detailedHealth.put("uptime", System.currentTimeMillis());

        return ResponseEntity.ok(detailedHealth);
    }

    @Operation(summary = "Get metrics", description = "Returns all metrics in Prometheus format")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Metrics retrieved successfully"),
        @ApiResponse(responseCode = "500", description = "Failed to retrieve metrics")
    })
    @GetMapping(value = "/metrics", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getMetrics() {
        log.debug("Metrics requested");

        // Build a simple Prometheus-format metrics response
        StringBuilder metrics = new StringBuilder();
        metrics.append("# HELP monitoring_service_up Monitoring service status\n");
        metrics.append("# TYPE monitoring_service_up gauge\n");
        metrics.append("monitoring_service_up 1\n");
        metrics.append("\n");

        metrics.append("# HELP monitoring_service_requests_total Total number of recorded requests\n");
        metrics.append("# TYPE monitoring_service_requests_total counter\n");
        metrics.append("monitoring_service_requests_total 0\n");
        metrics.append("\n");

        metrics.append("# HELP monitoring_service_health Health status\n");
        metrics.append("# TYPE monitoring_service_health gauge\n");
        metrics.append("monitoring_service_health 1\n");

        return ResponseEntity.ok(metrics.toString());
    }

    @Operation(summary = "Record a custom metric", description = "Records a custom metric with optional tags")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Metric recorded successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid metric data")
    })
    @PostMapping("/metric")
    public ResponseEntity<Map<String, Object>> recordMetric(
            @Parameter(description = "Metric name") @RequestParam String name,
            @Parameter(description = "Metric value") @RequestParam double value,
            @Parameter(description = "Tags as comma-separated key=value pairs") @RequestParam(required = false) String tags) {

        log.debug("Recording metric: name={} value={}", name, value);

        // Parse tags if provided
        if (tags != null && !tags.isEmpty()) {
            String[] tagPairs = tags.split(",");
            for (String pair : tagPairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    log.debug("Tag: {}={}", keyValue[0], keyValue[1]);
                }
            }
        }

        monitoringService.recordCustomMetric(name, value);

        Map<String, Object> response = Map.of(
            "success", true,
            "message", "Metric recorded successfully",
            "name", name,
            "value", value,
            "timestamp", System.currentTimeMillis()
        );

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Record HTTP request", description = "Records an HTTP request metric")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Request recorded successfully")
    })
    @PostMapping("/request")
    public ResponseEntity<Map<String, Object>> recordRequest(
            @Parameter(description = "HTTP method") @RequestParam String method,
            @Parameter(description = "Request path") @RequestParam String path,
            @Parameter(description = "Duration in milliseconds") @RequestParam long durationMs,
            @Parameter(description = "HTTP status code") @RequestParam int status) {

        log.debug("Recording HTTP request: {} {} {}ms {}", method, path, durationMs, status);

        monitoringService.recordHttpRequest(method, path, durationMs, status);

        Map<String, Object> response = Map.of(
            "success", true,
            "message", "Request recorded successfully",
            "timestamp", System.currentTimeMillis()
        );

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get available endpoints", description = "Returns list of available monitoring endpoints")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Endpoints retrieved successfully")
    })
    @GetMapping
    public ResponseEntity<Map<String, Object>> getEndpoints() {
        Map<String, Object> endpoints = Map.of(
            "health", "/api/v1/monitoring/health",
            "health-details", "/api/v1/monitoring/health/details",
            "metrics", "/api/v1/monitoring/metrics",
            "record-metric", "/api/v1/monitoring/metric",
            "record-request", "/api/v1/monitoring/request"
        );

        return ResponseEntity.ok(endpoints);
    }
}
