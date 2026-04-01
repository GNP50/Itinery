package com.travel.monitoring.strategy;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Console telemetry strategy implementation.
 * Logs all telemetry data to console with structured logging.
 */
@Slf4j
@Component("consoleStrategy")
@Data
public class ConsoleStrategy implements TelemetryStrategy {

    private final Map<String, TraceContext> activeTraces;
    private final AtomicBoolean initialized;
    private final AtomicLong requestCounter;
    private final Map<String, AtomicLong> metricsCounter;

    public ConsoleStrategy() {
        this.activeTraces = new ConcurrentHashMap<>();
        this.initialized = new AtomicBoolean(false);
        this.requestCounter = new AtomicLong(0);
        this.metricsCounter = new ConcurrentHashMap<>();
    }

    @Override
    public void recordRequest(String serviceName, String endpoint, long durationMs, int statusCode) {
        long seq = requestCounter.incrementAndGet();
        String timestamp = Instant.now().toString();

        System.out.println(String.format(
            "[TIMESTAMPS] %s [REQUEST] seq=%d service=%s endpoint=%s duration=%dms status=%d",
            timestamp, seq, serviceName, endpoint, durationMs, statusCode
        ));

        log.info("Request recorded: service={}, endpoint={}, duration={}ms, status={}",
            serviceName, endpoint, durationMs, statusCode);
    }

    @Override
    public void recordError(String serviceName, String endpoint, String errorMessage, String errorType) {
        String timestamp = Instant.now().toString();

        System.out.println(String.format(
            "[TIMESTAMPS] %s [ERROR] service=%s endpoint=%s type=%s message=%s",
            timestamp, serviceName, endpoint, errorType, errorMessage
        ));

        log.error("Error recorded: service={}, endpoint={}, type={}, message={}",
            serviceName, endpoint, errorType, errorMessage, new RuntimeException("Error context"));
    }

    @Override
    public void recordMetric(String name, double value, Map<String, String> tags) {
        String timestamp = Instant.now().toString();
        String tagsStr = tags != null ? tags.toString() : "{}";

        System.out.println(String.format(
            "[TIMESTAMPS] %s [METRIC] name=%s value=%.4f tags=%s",
            timestamp, name, value, tagsStr
        ));

        log.debug("Metric recorded: name={}, value={}, tags={}", name, value, tags);
    }

    @Override
    public void recordHistogram(String name, double value, Map<String, String> tags) {
        String timestamp = Instant.now().toString();
        String tagsStr = tags != null ? tags.toString() : "{}";

        System.out.println(String.format(
            "[TIMESTAMPS] %s [HISTOGRAM] name=%s value=%.4f tags=%s",
            timestamp, name, value, tagsStr
        ));

        log.debug("Histogram recorded: name={}, value={}, tags={}", name, value, tags);
    }

    @Override
    public void startTrace(String traceId, String operationName) {
        TraceContext context = new TraceContext();
        context.setTraceId(traceId);
        context.setSpanId(UUID.randomUUID().toString().replace("-", ""));
        context.setOperationName(operationName);
        context.setStartTime(Instant.now().toEpochMilli());

        activeTraces.put(traceId, context);

        String timestamp = Instant.now().toString();

        System.out.println(String.format(
            "[TIMESTAMPS] %s [TRACE_START] traceId=%s operation=%s spanId=%s",
            timestamp, traceId, operationName, context.getSpanId()
        ));

        log.info("Trace started: traceId={}, operation={}, spanId={}", traceId, operationName, context.getSpanId());
    }

    @Override
    public void endTrace(String traceId) {
        TraceContext context = activeTraces.remove(traceId);
        if (context != null) {
            long duration = Instant.now().toEpochMilli() - context.getStartTime();

            String timestamp = Instant.now().toString();

            System.out.println(String.format(
                "[TIMESTAMPS] %s [TRACE_END] traceId=%s operation=%s duration=%dms",
                timestamp, traceId, context.getOperationName(), duration
            ));

            log.info("Trace ended: traceId={}, operation={}, duration={}ms",
                traceId, context.getOperationName(), duration);
        } else {
            log.warn("Trace not found for traceId: {}", traceId);
        }
    }

    @Override
    public Map<String, Object> getHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "ok");
        health.put("service", "console-logger");
        health.put("initialized", initialized.get());
        health.put("activeTraces", activeTraces.size());
        health.put("requestCount", requestCounter.get());
        health.put("timestamp", Instant.now().toString());

        System.out.println("[HEALTH] Console strategy health check: " + health);

        return health;
    }

    @Data
    private static class TraceContext {
        private String traceId;
        private String spanId;
        private String operationName;
        private long startTime;
    }
}
