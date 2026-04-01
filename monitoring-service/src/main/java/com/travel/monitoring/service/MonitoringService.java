package com.travel.monitoring.service;

import com.travel.monitoring.strategy.TelemetryStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service layer for monitoring operations.
 * Provides methods to record HTTP requests, service errors, and custom metrics.
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class MonitoringService {

    private final TelemetryStrategy telemetryStrategy;

    /**
     * Records an HTTP request metric.
     *
     * @param method      the HTTP method (GET, POST, etc.)
     * @param path        the request path
     * @param durationMs  the request duration in milliseconds
     * @param status      the HTTP status code
     */
    public void recordHttpRequest(String method, String path, long durationMs, int status) {
        String serviceName = "monitoring-service";
        String endpoint = method + " " + path;

        telemetryStrategy.recordRequest(serviceName, endpoint, durationMs, status);
    }

    /**
     * Records a service error with exception details.
     *
     * @param service the name of the service where the error occurred
     * @param ex      the exception that was thrown
     */
    public void recordServiceError(String service, Throwable ex) {
        String endpoint = "unknown";
        String errorMessage = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        String errorType = ex.getClass().getSimpleName();

        telemetryStrategy.recordError(service, endpoint, errorMessage, errorType);
    }

    /**
     * Records a custom metric with default tags.
     *
     * @param name  the metric name
     * @param value the metric value
     */
    public void recordCustomMetric(String name, double value) {
        Map<String, String> tags = new HashMap<>();
        tags.put("source", "monitoring-service");
        tags.put("version", "1.0.0");

        telemetryStrategy.recordMetric(name, value, tags);
    }

    /**
     * Records a custom histogram metric.
     *
     * @param name  the metric name
     * @param value the metric value
     */
    public void recordCustomHistogram(String name, double value) {
        Map<String, String> tags = new HashMap<>();
        tags.put("source", "monitoring-service");

        telemetryStrategy.recordHistogram(name, value, tags);
    }

    /**
     * Records a request with service context.
     *
     * @param serviceName the name of the service making the request
     * @param endpoint    the endpoint being called
     * @param durationMs  the request duration
     * @param status      the response status code
     */
    public void recordServiceRequest(String serviceName, String endpoint, long durationMs, int status) {
        telemetryStrategy.recordRequest(serviceName, endpoint, durationMs, status);
    }

    /**
     * Gets the current system health status.
     *
     * @return a map containing health information
     */
    public Map<String, Object> getSystemHealth() {
        return telemetryStrategy.getHealth();
    }

    /**
     * Starts a new trace for distributed tracing.
     *
     * @param traceId       the unique trace identifier
     * @param operationName the operation name to trace
     */
    public void startTrace(String traceId, String operationName) {
        telemetryStrategy.startTrace(traceId, operationName);
    }

    /**
     * Ends a trace.
     *
     * @param traceId the unique trace identifier
     */
    public void endTrace(String traceId) {
        telemetryStrategy.endTrace(traceId);
    }
}
