package com.travel.monitoring.strategy;

import java.util.Map;

/**
 * Strategy interface for telemetry operations.
 * Defines the contract for different telemetry implementations
 * such as Datadog, Console, and OpenTelemetry.
 */
public interface TelemetryStrategy {

    /**
     * Records an HTTP request metric.
     *
     * @param serviceName   the name of the service handling the request
     * @param endpoint      the HTTP endpoint path
     * @param durationMs    the request duration in milliseconds
     * @param statusCode    the HTTP status code
     */
    void recordRequest(String serviceName, String endpoint, long durationMs, int statusCode);

    /**
     * Records an error event.
     *
     * @param serviceName    the name of the service where the error occurred
     * @param endpoint       the endpoint where the error occurred
     * @param errorMessage   the error message
     * @param errorType      the type/classification of the error
     */
    void recordError(String serviceName, String endpoint, String errorMessage, String errorType);

    /**
     * Records a generic metric with tags.
     *
     * @param name   the metric name
     * @param value  the metric value
     * @param tags   additional tags for the metric
     */
    void recordMetric(String name, double value, Map<String, String> tags);

    /**
     * Records a histogram metric with tags.
     *
     * @param name   the metric name
     * @param value  the metric value
     * @param tags   additional tags for the metric
     */
    void recordHistogram(String name, double value, Map<String, String> tags);

    /**
     * Starts a new trace span.
     *
     * @param traceId      the unique trace identifier
     * @param operationName the name of the operation being traced
     */
    void startTrace(String traceId, String operationName);

    /**
     * Ends the current trace span.
     *
     * @param traceId the unique trace identifier
     */
    void endTrace(String traceId);

    /**
     * Returns health check data for the telemetry system.
     *
     * @return a map containing health status and details
     */
    Map<String, Object> getHealth();
}
