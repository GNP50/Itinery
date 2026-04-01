package com.travel.saga.statemachine;

import com.travel.saga.domain.SagaInstance;
import com.travel.saga.port.outbound.SagaPersistencePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Slf4j
public class SagaActions {

    private final SagaPersistencePort<SagaInstance> persistencePort;

    public SagaActions(SagaPersistencePort<SagaInstance> persistencePort) {
        this.persistencePort = persistencePort;
    }

    public Action<SagaStates, SagaEvents> startAction() {
        return (context) -> {
            String messageId = context.getMessage() != null ? context.getMessage().getHeaders().getId().toString() : "manual";
            log.info("Starting saga for message: {}", messageId);
        };
    }

    public Action<SagaStates, SagaEvents> geocodingCompletedAction() {
        return (context) -> {
            SagaInstance instance = getSagaInstance(context);
            instance.addCompletedStep("GEOCODING");
            instance.updateState("GEOCODING_COMPLETED");
            persistencePort.save(instance);
            log.info("Geocoding completed for saga instance: {}", instance.getId());
        };
    }

    public Action<SagaStates, SagaEvents> geocodingFailedAction() {
        return (context) -> {
            SagaInstance instance = getSagaInstance(context);
            instance.setFailedStep("GEOCODING");
            if (context.getMessage() != null && context.getMessage().getHeaders().containsKey("errorMessage")) {
                instance.setErrorMessage(context.getMessage().getHeaders().get("errorMessage", String.class));
            }
            if (instance.canRetry()) {
                instance.incrementRetryCount();
                log.warn("Geocoding failed for saga instance: {} (attempt {}/{}), scheduling compensation",
                        instance.getId(), instance.getRetryCount(), instance.getMaxRetries());
                sendCompensateEvent(instance);
            } else {
                log.error("Geocoding failed for saga instance: {} after {} retries", instance.getId(), instance.getRetryCount());
            }
            persistencePort.save(instance);
        };
    }

    public Action<SagaStates, SagaEvents> routingCompletedAction() {
        return (context) -> {
            SagaInstance instance = getSagaInstance(context);
            instance.addCompletedStep("ROUTING");
            instance.updateState("ROUTING_COMPLETED");
            persistencePort.save(instance);
            log.info("Routing completed for saga instance: {}", instance.getId());
        };
    }

    public Action<SagaStates, SagaEvents> routingFailedAction() {
        return (context) -> {
            SagaInstance instance = getSagaInstance(context);
            instance.setFailedStep("ROUTING");
            if (context.getMessage() != null && context.getMessage().getHeaders().containsKey("errorMessage")) {
                instance.setErrorMessage(context.getMessage().getHeaders().get("errorMessage", String.class));
            }
            if (instance.canRetry()) {
                instance.incrementRetryCount();
                log.warn("Routing failed for saga instance: {} (attempt {}/{}), scheduling compensation",
                        instance.getId(), instance.getRetryCount(), instance.getMaxRetries());
                sendCompensateEvent(instance);
            } else {
                log.error("Routing failed for saga instance: {} after {} retries", instance.getId(), instance.getRetryCount());
            }
            persistencePort.save(instance);
        };
    }

    public Action<SagaStates, SagaEvents> aiCompletedAction() {
        return (context) -> {
            SagaInstance instance = getSagaInstance(context);
            instance.addCompletedStep("AI_ENRICHMENT");
            instance.updateState("AI_ENRICHMENT_COMPLETED");
            persistencePort.save(instance);
            log.info("AI enrichment completed for saga instance: {}", instance.getId());
        };
    }

    public Action<SagaStates, SagaEvents> aiFailedAction() {
        return (context) -> {
            SagaInstance instance = getSagaInstance(context);
            instance.setFailedStep("AI_ENRICHMENT");
            if (context.getMessage() != null && context.getMessage().getHeaders().containsKey("errorMessage")) {
                instance.setErrorMessage(context.getMessage().getHeaders().get("errorMessage", String.class));
            }
            if (instance.canRetry()) {
                instance.incrementRetryCount();
                log.warn("AI enrichment failed for saga instance: {} (attempt {}/{}), scheduling compensation",
                        instance.getId(), instance.getRetryCount(), instance.getMaxRetries());
                sendCompensateEvent(instance);
            } else {
                log.error("AI enrichment failed for saga instance: {} after {} retries", instance.getId(), instance.getRetryCount());
            }
            persistencePort.save(instance);
        };
    }

    public Action<SagaStates, SagaEvents> poiCompletedAction() {
        return (context) -> {
            SagaInstance instance = getSagaInstance(context);
            instance.addCompletedStep("POI_DISCOVERY");
            instance.updateState("COMPLETED");
            instance.setFailedStep(null);
            instance.setErrorMessage(null);
            persistencePort.save(instance);
            log.info("POI discovery completed for saga instance: {}", instance.getId());
        };
    }

    public Action<SagaStates, SagaEvents> poiFailedAction() {
        return (context) -> {
            SagaInstance instance = getSagaInstance(context);
            instance.setFailedStep("POI_DISCOVERY");
            if (context.getMessage() != null && context.getMessage().getHeaders().containsKey("errorMessage")) {
                instance.setErrorMessage(context.getMessage().getHeaders().get("errorMessage", String.class));
            }
            if (instance.canRetry()) {
                instance.incrementRetryCount();
                log.warn("POI discovery failed for saga instance: {} (attempt {}/{}), scheduling compensation",
                        instance.getId(), instance.getRetryCount(), instance.getMaxRetries());
                sendCompensateEvent(instance);
            } else {
                log.error("POI discovery failed for saga instance: {} after {} retries", instance.getId(), instance.getRetryCount());
            }
            persistencePort.save(instance);
        };
    }

    public Action<SagaStates, SagaEvents> compensationCompletedAction() {
        return (context) -> {
            SagaInstance instance = getSagaInstance(context);
            instance.updateState("FAILED");
            instance.getCompletedSteps().clear();
            persistencePort.save(instance);
            log.info("Compensation completed for saga instance: {}", instance.getId());
        };
    }

    private SagaInstance getSagaInstance(StateContext<SagaStates, SagaEvents> context) {
        Object instance = context.getStateMachine().getExtendedState().getVariables().get("sagaInstance");
        if (instance instanceof SagaInstance) {
            return (SagaInstance) instance;
        }
        throw new IllegalStateException("SagaInstance not found in extended state");
    }

    private void sendCompensateEvent(SagaInstance instance) {
        log.info("Sending COMPENSATE event for saga instance: {}", instance.getId());
    }
}
