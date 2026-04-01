package com.travel.geo.impl.exception;

/**
 * Runtime exception thrown when an external service (geocoding, routing, POI)
 * returns an error or is unavailable.
 */
public class ExternalServiceException extends RuntimeException {

    private final String serviceName;

    public ExternalServiceException(String serviceName, String message) {
        super(serviceName + ": " + message);
        this.serviceName = serviceName;
    }

    public ExternalServiceException(String serviceName, String message, Throwable cause) {
        super(serviceName + ": " + message, cause);
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }
}
