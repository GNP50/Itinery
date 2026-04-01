package com.travel.monitoring.grpc;

import com.travel.monitoring.service.MonitoringService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * gRPC service implementation for monitoring operations.
 * Provides RPC endpoints for recording metrics, health checks, and trace management.
 */
@Slf4j
@GrpcService
public class MonitoringGrpcServiceImpl extends MonitoringServiceGrpc.MonitoringServiceImplBase {

    private final MonitoringService monitoringService;
    private final AtomicLong metricCounter;

    @Autowired
    public MonitoringGrpcServiceImpl(MonitoringService monitoringService) {
        this.monitoringService = monitoringService;
        this.metricCounter = new AtomicLong(0);
    }

    @Override
    public void recordMetric(RecordMetricRequest request,
                             StreamObserver<RecordMetricResponse> responseObserver) {
        try {
            String name = request.getName();
            double value = request.getValue();
            String serviceName = request.getServiceName();

            // Record the metric
            monitoringService.recordCustomMetric(name, value);
            metricCounter.incrementAndGet();

            RecordMetricResponse response = RecordMetricResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Metric recorded successfully")
                .setTimestamp(System.currentTimeMillis())
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Failed to record metric", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("Failed to record metric: " + e.getMessage())
                .asRuntimeException());
        }
    }

    @Override
    public void getHealth(HealthRequest request,
                          StreamObserver<HealthResponse> responseObserver) {
        try {
            Map<String, Object> health = monitoringService.getSystemHealth();

            HealthResponse response = HealthResponse.newBuilder()
                .setStatus(health.getOrDefault("status", "unknown").toString())
                .setService(request.getService().isEmpty() ? "monitoring-service" : request.getService())
                .putAllDetails(convertMapToStringMap(health))
                .setTimestamp(System.currentTimeMillis())
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Failed to get health", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("Failed to get health: " + e.getMessage())
                .asRuntimeException());
        }
    }

    @Override
    public void getMetrics(MetricsRequest request,
                           StreamObserver<MetricsResponse> responseObserver) {
        try {
            // Get health to determine if we're using Datadog or OpenTelemetry
            Map<String, Object> health = monitoringService.getSystemHealth();
            String format = request.getFormat().isEmpty() ? "prometheus" : request.getFormat();

            // Build metrics snapshot
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("metricCount", metricCounter.get());
            metrics.put("timestamp", System.currentTimeMillis());
            metrics.put("health", health);

            MetricsResponse response = MetricsResponse.newBuilder()
                .setFormat(format)
                .setData(metrics.toString())
                .setTimestamp(System.currentTimeMillis())
                .setMetricCount(1)
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Failed to get metrics", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("Failed to get metrics: " + e.getMessage())
                .asRuntimeException());
        }
    }

    @Override
    public void startTrace(StartTraceRequest request,
                           StreamObserver<StartTraceResponse> responseObserver) {
        try {
            String traceId = request.getTraceId();
            String operationName = request.getOperationName();

            if (traceId.isEmpty()) {
                traceId = "trace-" + System.currentTimeMillis();
            }

            monitoringService.startTrace(traceId, operationName);

            StartTraceResponse response = StartTraceResponse.newBuilder()
                .setTraceId(traceId)
                .setSpanId("span-" + UUID.randomUUID().toString().replace("-", ""))
                .setSuccess(true)
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Failed to start trace", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("Failed to start trace: " + e.getMessage())
                .asRuntimeException());
        }
    }

    @Override
    public void endTrace(EndTraceRequest request,
                         StreamObserver<EndTraceResponse> responseObserver) {
        try {
            String traceId = request.getTraceId();

            if (traceId.isEmpty()) {
                responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Trace ID is required")
                    .asRuntimeException());
                return;
            }

            monitoringService.endTrace(traceId);

            EndTraceResponse response = EndTraceResponse.newBuilder()
                .setTraceId(traceId)
                .setSuccess(true)
                .setDurationMs(0)  // Would need to track this
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Failed to end trace", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("Failed to end trace: " + e.getMessage())
                .asRuntimeException());
        }
    }

    private Map<String, String> convertMapToStringMap(Map<String, Object> map) {
        Map<String, String> result = new HashMap<>();
        if (map != null) {
            map.forEach((key, value) -> result.put(key, String.valueOf(value)));
        }
        return result;
    }
}
