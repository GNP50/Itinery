package com.travel.saga.statemachine;

import com.travel.saga.domain.SagaInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineBuilder;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;

import java.util.EnumSet;

@Configuration
@Slf4j
public class ItinerarySagaConfig {

    @Autowired
    private SagaActions sagaActions;

    @Bean
    public StateMachineBuilder.Builder<SagaStates, SagaEvents> sagaStateMachineBuilder() throws Exception {
        StateMachineBuilder.Builder<SagaStates, SagaEvents> builder = StateMachineBuilder.builder();

        builder.configureConfiguration()
            .withConfiguration()
                .machineId("itineraryStateMachine")
                .autoStartup(true);

        builder.configureStates()
            .withStates()
                .initial(SagaStates.INITIAL)
                .end(SagaStates.COMPLETED)
                .end(SagaStates.FAILED)
                .states(EnumSet.allOf(SagaStates.class));

        builder.configureTransitions()
            .withExternal()
                .event(SagaEvents.START)
                .source(SagaStates.INITIAL)
                .target(SagaStates.GEOCODING)
                .action(sagaActions.startAction())
            .and()
            .withExternal()
                .event(SagaEvents.GEOCODING_COMPLETED)
                .source(SagaStates.GEOCODING)
                .target(SagaStates.ROUTING)
                .action(sagaActions.geocodingCompletedAction())
            .and()
            .withExternal()
                .event(SagaEvents.GEOCODING_FAILED)
                .source(SagaStates.GEOCODING)
                .target(SagaStates.COMPENSATING)
                .action(sagaActions.geocodingFailedAction())
            .and()
            .withExternal()
                .event(SagaEvents.ROUTING_COMPLETED)
                .source(SagaStates.ROUTING)
                .target(SagaStates.AI_ENRICHMENT)
                .action(sagaActions.routingCompletedAction())
            .and()
            .withExternal()
                .event(SagaEvents.ROUTING_FAILED)
                .source(SagaStates.ROUTING)
                .target(SagaStates.COMPENSATING)
                .action(sagaActions.routingFailedAction())
            .and()
            .withExternal()
                .event(SagaEvents.AI_COMPLETED)
                .source(SagaStates.AI_ENRICHMENT)
                .target(SagaStates.POI_DISCOVERY)
                .action(sagaActions.aiCompletedAction())
            .and()
            .withExternal()
                .event(SagaEvents.AI_FAILED)
                .source(SagaStates.AI_ENRICHMENT)
                .target(SagaStates.COMPENSATING)
                .action(sagaActions.aiFailedAction())
            .and()
            .withExternal()
                .event(SagaEvents.POI_COMPLETED)
                .source(SagaStates.POI_DISCOVERY)
                .target(SagaStates.COMPLETED)
                .action(sagaActions.poiCompletedAction())
            .and()
            .withExternal()
                .event(SagaEvents.POI_FAILED)
                .source(SagaStates.POI_DISCOVERY)
                .target(SagaStates.FAILED)
                .action(sagaActions.poiFailedAction())
            .and()
            .withExternal()
                .event(SagaEvents.COMPENSATION_COMPLETED)
                .source(SagaStates.COMPENSATING)
                .target(SagaStates.FAILED)
                .action(sagaActions.compensationCompletedAction());

        return builder;
    }

    @Bean
    public StateMachineListenerAdapter<SagaStates, SagaEvents> stateMachineListener() {
        return new StateMachineListenerAdapter<SagaStates, SagaEvents>() {
            @Override
            public void stateChanged(State<SagaStates, SagaEvents> from, State<SagaStates, SagaEvents> to) {
                log.debug("State changed from {} to {}", from == null ? null : from.getId(), to == null ? null : to.getId());
            }

            @Override
            public void eventNotAccepted(Message<SagaEvents> event) {
                log.warn("Event {} not accepted by state machine", event.getPayload());
            }
        };
    }

    @Bean
    public StateMachine<SagaStates, SagaEvents> sagaStateMachine() throws Exception {
        StateMachineBuilder.Builder<SagaStates, SagaEvents> builder = sagaStateMachineBuilder();
        StateMachine<SagaStates, SagaEvents> stateMachine = builder.build();
        stateMachine.addStateListener(stateMachineListener());
        return stateMachine;
    }
}
