package com.travel.monitoring.strategy;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * OpenTelemetry telemetry strategy implementation.
 * Uses OpenTelemetry SDK for tracing and metrics collection.
 */
@Slf4j
@Component("openTelemetryStrategy")
@Data
public class OpenTelemetryStrategy implements TelemetryStrategy {

    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;
    private final Meter meter;
    private final Map<String, TraceContext> activeTraces;
    private final AtomicLong requestCounter;
    private final Map<String, AtomicLong> metricCounters;

    public OpenTelemetryStrategy(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
        this.tracer = openTelemetry.getTracer("monitoring-service");
        this.meter = openTelemetry.getMeter("monitoring-service");
        this.activeTraces = new ConcurrentHashMap<>();
        this.requestCounter = new AtomicLong(0);
        this.metricCounters = new ConcurrentHashMap<>();
    }

    @Override
    public void recordRequest(String serviceName, String endpoint, long durationMs, int statusCode) {
        long seq = requestCounter.incrementAndGet();

        // Record metrics using OpenTelemetry
        Attributes attributes = buildAttributes(serviceName, endpoint, statusCode, "HTTP");

        // Record histogram for duration
        meter.histogramBuilder("http.request.duration")
            .setUnit("milliseconds")
            .setDescription("HTTP request duration")
            .build()
            .record(durationMs, attributes);

        // Record counter for request count
        meter.counterBuilder("http.request.count")
            .setUnit("requests")
            .setDescription("HTTP request count")
            .build()
            .add(1, attributes);

        // Log the request
        log.info("HTTP request recorded: seq={} service={} endpoint={} duration={}ms status={}",
            seq, serviceName, endpoint, durationMs, statusCode);
    }

    @Override
    public void recordError(String serviceName, String endpoint, String errorMessage, String errorType) {
        String traceId = "trace-" + UUID.randomUUID();
        String spanId = "span-" + UUID.randomUUID().toString().replace("-", "");

        log.error("Error recorded: service={} endpoint={} type={} message={}",
            serviceName, endpoint, errorType, errorMessage);

        // Create a span for the error
        Span span = tracer.spanBuilder("error")
            .setAttribute("service.name", serviceName)
            .setAttribute("endpoint", endpoint)
            .setAttribute("error.type", errorType)
            .setAttribute("error.message", errorMessage)
            .startSpan();

        // Record exception event
        span.recordException(new RuntimeException(errorMessage));
        span.end();
    }

    @Override
    public void recordMetric(String name, double value, Map<String, String> tags) {
        Attributes attributes = buildAttributes(tags);

        // Create or get counter for this metric
        String key = "metric:" + name;
        AtomicLong counter = metricCounters.computeIfAbsent(key, k -> new AtomicLong(0));

        log.debug("Metric recorded: name={} value={} tags={}", name, value, tags);
    }

    @Override
    public void recordHistogram(String name, double value, Map<String, String> tags) {
        Attributes attributes = buildAttributes(tags);

        meter.histogramBuilder(name)
            .setUnit("units")
            .setDescription("Custom histogram metric")
            .build()
            .record(value, attributes);

        log.debug("Histogram recorded: name={} value={} tags={}", name, value, tags);
    }

    @Override
    public void startTrace(String traceId, String operationName) {
        Span span = tracer.spanBuilder(operationName)
            .setAttribute("trace.id", traceId)
            .setAttribute("span.kind", "server")
            .startSpan();

        TraceContext context = new TraceContext();
        context.setTraceId(traceId);
        context.setSpanId(span.getSpanContext().getSpanId());
        context.setOperationName(operationName);
        context.setStartTime(Instant.now().toEpochMilli());
        context.setSpan(span);

        activeTraces.put(traceId, context);

        try (Scope scope = span.makeCurrent()) {
            log.info("Trace started: traceId={} operation={} spanId={}",
                traceId, operationName, context.getSpanId());
        }
    }

    @Override
    public void endTrace(String traceId) {
        TraceContext context = activeTraces.remove(traceId);
        if (context != null) {
            Span span = context.getSpan();
            if (span != null && !span.getSpanContext().isValid()) {
                long duration = Instant.now().toEpochMilli() - context.getStartTime();

                span.setAttribute("duration.ms", duration)
                    .end();

                log.info("Trace ended: traceId={} operation={} duration={}ms",
                    traceId, context.getOperationName(), duration);
            }
        } else {
            log.warn("Trace not found for traceId: {}", traceId);
        }
    }

    @Override
    public Map<String, Object> getHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "up");
        health.put("service", "otel-collector");
        health.put("openTelemetryVersion", "1.41.0");
        health.put("activeTraces", activeTraces.size());
        health.put("requestCount", requestCounter.get());
        health.put("timestamp", Instant.now().toString());

        log.debug("Health check response: {}", health);

        return health;
    }

    private Attributes buildAttributes(String serviceName, String endpoint, int statusCode, String method) {
        AttributesBuilder builder = Attributes.builder();
        builder.put("service.name", serviceName);
        builder.put("http.endpoint", endpoint);
        builder.put("http.status_code", statusCode);
        builder.put("http.method", method);
        return builder.build();
    }

    private Attributes buildAttributes(Map<String, String> tags) {
        AttributesBuilder builder = Attributes.builder();
        if (tags != null) {
            tags.forEach(builder::put);
        }
        return builder.build();
    }

    @Data
    private static class TraceContext {
        private String traceId;
        private String spanId;
        private String operationName;
        private long startTime;
        private Span span;
    }
}
