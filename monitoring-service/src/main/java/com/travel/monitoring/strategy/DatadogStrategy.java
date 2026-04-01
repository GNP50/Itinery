package com.travel.monitoring.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Datadog telemetry strategy implementation.
 * Sends metrics, traces, and error events to Datadog via HTTP API.
 */
@Slf4j
@Component("datadogStrategy")
@Data
public class DatadogStrategy implements TelemetryStrategy {

    private static final String DATADOG_API_V1_SERIES = "/api/v1/series";
    private static final String DATADOG_API_V1_EVENTS = "/api/v1/events";
    private static final String DATADOG_API_V1_TAGS = "/api/v1/tags";

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final Map<String, TraceContext> activeTraces;

    @Value("${monitoring.datadog.api-key:}")
    private String apiKey;

    @Value("${monitoring.datadog.site:datadoghq.com}")
    private String site;

    @Value("${monitoring.datadog.host:localhost}")
    private String host;

    @Value("${monitoring.datadog.port:8126}")
    private int port;

    private final AtomicBoolean initialized;

    public DatadogStrategy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restTemplate = createRestTemplate();
        this.activeTraces = new ConcurrentHashMap<>();
        this.initialized = new AtomicBoolean(false);
    }

    private RestTemplate createRestTemplate() {
        ClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(java.net.HttpURLConnection connection,
                                             String httpMethod) throws java.io.IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(10000);
            }
        };
        return new RestTemplate(factory);
    }

    @Override
    public void recordRequest(String serviceName, String endpoint, long durationMs, int statusCode) {
        try {
            Map<String, String> tags = new HashMap<>();
            tags.put("service", serviceName);
            tags.put("endpoint", endpoint);
            tags.put("status_code", String.valueOf(statusCode));
            tags.put("method", "HTTP");

            recordMetric("http.request.duration", durationMs, tags);
            recordMetric("http.request.count", 1.0, tags);

            if (statusCode >= 500) {
                recordError(serviceName, endpoint,
                    "HTTP " + statusCode + " error", "ServerError");
            } else if (statusCode >= 400) {
                recordError(serviceName, endpoint,
                    "HTTP " + statusCode + " error", "ClientError");
            }
        } catch (Exception e) {
            log.error("Failed to record HTTP request metric for Datadog", e);
        }
    }

    @Override
    public void recordError(String serviceName, String endpoint, String errorMessage, String errorType) {
        try {
            if (apiKey.isEmpty()) {
                log.warn("Datadog API key not configured. Error event not sent: {}", errorMessage);
                return;
            }

            Map<String, Object> event = new HashMap<>();
            event.put("alert_type", "error");
            event.put("title", "[" + serviceName + "] " + errorType);
            event.put("text", "Endpoint: " + endpoint + "\nError: " + errorMessage);
            event.put("tags", Arrays.asList(
                "service:" + serviceName,
                "endpoint:" + endpoint,
                "type:" + errorType
            ));
            event.put("timestamp", Instant.now().getEpochSecond());

            String url = String.format("https://api.%s%s", site, DATADOG_API_V1_EVENTS);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("DD-API-KEY", apiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(event, headers);
            restTemplate.postForEntity(url, request, String.class);
        } catch (Exception e) {
            log.error("Failed to record error event for Datadog", e);
        }
    }

    @Override
    public void recordMetric(String name, double value, Map<String, String> tags) {
        try {
            if (apiKey.isEmpty()) {
                log.debug("Datadog API key not configured. Metric not sent: {}={}", name, value);
                return;
            }

            List<Map<String, Object>> metrics = new ArrayList<>();
            Map<String, Object> metric = new HashMap<>();
            metric.put("metric", name);
            metric.put("points", Arrays.asList(Arrays.asList(
                Instant.now().getEpochSecond(),
                value
            )));
            metric.put("type", "gauge");
            metric.put("host", host);
            metric.put("tags", buildTagsList(tags));

            metrics.add(metric);

            String url = String.format("https://api.%s%s", site, DATADOG_API_V1_SERIES);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("DD-API-KEY", apiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("series", metrics);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(url, request, String.class);
        } catch (Exception e) {
            log.error("Failed to record metric for Datadog: {}={}", name, value, e);
        }
    }

    @Override
    public void recordHistogram(String name, double value, Map<String, String> tags) {
        try {
            if (apiKey.isEmpty()) {
                log.debug("Datadog API key not configured. Histogram not sent: {}={}", name, value);
                return;
            }

            List<Map<String, Object>> metrics = new ArrayList<>();
            Map<String, Object> metric = new HashMap<>();
            metric.put("metric", name);
            metric.put("points", Arrays.asList(Arrays.asList(
                Instant.now().getEpochSecond(),
                value
            )));
            metric.put("type", "histogram");
            metric.put("host", host);
            metric.put("tags", buildTagsList(tags));

            metrics.add(metric);

            String url = String.format("https://api.%s%s", site, DATADOG_API_V1_SERIES);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("DD-API-KEY", apiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("series", metrics);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(url, request, String.class);
        } catch (Exception e) {
            log.error("Failed to record histogram for Datadog: {}={}", name, value, e);
        }
    }

    @Override
    public void startTrace(String traceId, String operationName) {
        try {
            TraceContext context = new TraceContext();
            context.setTraceId(traceId);
            context.setSpanId(UUID.randomUUID().toString().replace("-", ""));
            context.setOperationName(operationName);
            context.setStartTime(Instant.now().toEpochMilli());

            activeTraces.put(traceId, context);

            log.debug("Started trace: traceId={}, operation={}", traceId, operationName);
        } catch (Exception e) {
            log.error("Failed to start trace", e);
        }
    }

    @Override
    public void endTrace(String traceId) {
        try {
            TraceContext context = activeTraces.remove(traceId);
            if (context != null) {
                long duration = Instant.now().toEpochMilli() - context.getStartTime();

                recordMetric("trace.duration", duration, Map.of(
                    "trace_id", traceId,
                    "operation", context.getOperationName()
                ));

                log.debug("Ended trace: traceId={}, duration={}ms", traceId, duration);
            }
        } catch (Exception e) {
            log.error("Failed to end trace", e);
        }
    }

    @Override
    public Map<String, Object> getHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "unknown");

        try {
            if (apiKey.isEmpty()) {
                health.put("status", "ok");
                health.put("reason", "API key not configured - using local logging");
                return health;
            }

            String url = String.format("https://api.%s%s", site, DATADOG_API_V1_TAGS);
            HttpHeaders headers = new HttpHeaders();
            headers.set("DD-API-KEY", apiKey);

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                health.put("status", "up");
                health.put("datadog_site", site);
                health.put("last_check", Instant.now().toString());
            } else {
                health.put("status", "down");
                health.put("reason", "Unexpected response code: " + response.getStatusCode());
            }
        } catch (Exception e) {
            health.put("status", "down");
            health.put("reason", "Failed to connect to Datadog: " + e.getMessage());
            log.error("Datadog health check failed", e);
        }

        return health;
    }

    private List<String> buildTagsList(Map<String, String> tags) {
        List<String> tagList = new ArrayList<>();
        if (tags != null) {
            tags.forEach((key, value) -> tagList.add(key + ":" + value));
        }
        return tagList;
    }

    @Data
    private static class TraceContext {
        private String traceId;
        private String spanId;
        private String operationName;
        private long startTime;
    }
}
