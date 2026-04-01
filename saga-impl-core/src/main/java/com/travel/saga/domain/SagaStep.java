package com.travel.saga.domain;

public enum SagaStep {
    GEOCODING,
    ROUTING,
    AI_ENRICHMENT,
    POI_DISCOVERY,
    COMPLETED,
    FAILED
}
