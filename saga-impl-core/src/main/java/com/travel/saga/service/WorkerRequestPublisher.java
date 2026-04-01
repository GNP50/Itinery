package com.travel.saga.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.itinerary.api.kafka.WorkerRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Publishes {@link WorkerRequestEvent} messages to the worker-specific Kafka
 * topics that drive each processing stage.
 *
 * <p>Topic conventions with priority-based routing:
 * <ul>
 *   <li>{@code geo.worker.request.admin}       – geocoding (ADMIN priority)</li>
 *   <li>{@code geo.worker.request.registered}  – geocoding (REGISTERED priority)</li>
 *   <li>{@code geo.worker.request.anonymous}   – geocoding (ANONYMOUS priority)</li>
 *   <li>Same pattern for {@code route}, {@code ai}, {@code poi} topics</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkerRequestPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void requestGeoProcessing(UUID itineraryId, String userType, String preferencesJson, long sagaVersion) {
        String topic = buildTopicName("geo.worker.request", userType);
        send(topic, itineraryId, "GEO", preferencesJson, sagaVersion, userType);
    }

    public void requestRouteProcessing(UUID itineraryId, String userType, String preferencesJson, long sagaVersion) {
        String topic = buildTopicName("route.worker.request", userType);
        send(topic, itineraryId, "ROUTE", preferencesJson, sagaVersion, userType);
    }

    public void requestAiProcessing(UUID itineraryId, String userType, String preferencesJson, long sagaVersion) {
        String topic = buildTopicName("ai.worker.request", userType);
        send(topic, itineraryId, "AI", preferencesJson, sagaVersion, userType);
    }

    public void requestPoiProcessing(UUID itineraryId, String userType, String preferencesJson, long sagaVersion) {
        String topic = buildTopicName("poi.worker.request", userType);
        send(topic, itineraryId, "POI", preferencesJson, sagaVersion, userType);
    }

    /**
     * Builds the priority-based topic name by appending the user type suffix.
     *
     * @param baseTopic the base topic name (e.g., "geo.worker.request")
     * @param userType  the user type (ADMIN, REGISTERED, ANONYMOUS), defaults to ANONYMOUS if null
     * @return the full topic name with suffix (e.g., "geo.worker.request.admin")
     */
    private String buildTopicName(String baseTopic, String userType) {
        String suffix = (userType != null) ? userType.toLowerCase() : "anonymous";
        return baseTopic + "." + suffix;
    }

    private void send(String topic, UUID itineraryId, String workerType, String preferencesJson, long sagaVersion, String userType) {
        try {
            WorkerRequestEvent event = new WorkerRequestEvent(itineraryId, workerType, preferencesJson, sagaVersion, userType);
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, itineraryId.toString(), json);
            log.info("WorkerRequestPublisher: sent {} request for itinerary id={} (sagaVersion={}, userType={}, topic={})",
                     workerType, itineraryId, sagaVersion, userType, topic);
        } catch (Exception ex) {
            log.error("WorkerRequestPublisher: failed to send {} request for id={}: {}",
                      workerType, itineraryId, ex.getMessage(), ex);
        }
    }
}
