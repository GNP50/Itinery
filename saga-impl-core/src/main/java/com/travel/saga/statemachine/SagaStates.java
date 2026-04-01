package com.travel.saga.statemachine;

public enum SagaStates {
    INITIAL,
    GEOCODING,
    ROUTING,
    AI_ENRICHMENT,
    POI_DISCOVERY,
    COMPLETED,
    FAILED,
    COMPENSATING
}
