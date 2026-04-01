package com.travel.saga.listener;

import com.travel.saga.domain.DlqMessage;
import com.travel.saga.repository.DlqMessageRepository;
import com.travel.saga.statemachine.SagaEvents;
import com.travel.saga.statemachine.SagaStates;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Component
@Slf4j
public class DlqListener {

    private final DlqMessageRepository dlqMessageRepository;
    private final StateMachine<SagaStates, SagaEvents> sagaStateMachine;

    public DlqListener(DlqMessageRepository dlqMessageRepository, StateMachine<SagaStates, SagaEvents> sagaStateMachine) {
        this.dlqMessageRepository = dlqMessageRepository;
        this.sagaStateMachine = sagaStateMachine;
    }

    @KafkaListener(topics = "dlq.itinerary-events", groupId = "saga-dlq-listener")
    @Transactional
    public void handleDLQMessage(String payload) {
        log.info("Received DLQ message: {}", payload);
        DlqMessage dlqMessage = DlqMessage.builder()
                .id(UUID.randomUUID())
                .message(payload)
                .timestamp(Instant.now())
                .processed(false)
                .build();
        dlqMessageRepository.save(dlqMessage);
        log.info("DLQ message saved with ID: {}", dlqMessage.getId());
    }

    @KafkaListener(topics = "dlq.itinerary-events", groupId = "saga-dlq-replay")
    @Transactional
    public void handleDLQForReplay(Message<String> message) {
        MessageHeaders headers = message.getHeaders();
        log.info("Received DLQ message for replay: {} with headers: {}", message.getPayload(), headers);

        DlqMessage dlqMessage = DlqMessage.builder()
                .id(UUID.randomUUID())
                .message(message.getPayload())
                .topic(headers.get("kafka_topic", String.class))
                .key(headers.get("kafka_key", String.class))
                .timestamp(Instant.now())
                .processed(false)
                .build();
        dlqMessageRepository.save(dlqMessage);
        log.info("DLQ message saved for replay with ID: {}", dlqMessage.getId());
    }

    public void replayMessage(UUID messageId) {
        DlqMessage dlqMessage = dlqMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("DLQ message not found: " + messageId));

        log.info("Replaying DLQ message: {}", messageId);

        SagaEvents eventType = SagaEvents.START;
        try {
            eventType = SagaEvents.valueOf(dlqMessage.getMessage().trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            log.warn("Cannot parse SagaEvent from message '{}', defaulting to START", dlqMessage.getMessage());
        }

        Message<SagaEvents> replayMessage = MessageBuilder.withPayload(eventType)
                .setHeader("timestamp", Instant.now())
                .setHeader("dlq_message_id", messageId)
                .build();

        sagaStateMachine.sendEvent(Mono.just(replayMessage)).blockLast();

        dlqMessage.setProcessed(true);
        dlqMessage.setProcessedAt(Instant.now());
        dlqMessageRepository.save(dlqMessage);

        log.info("DLQ message replayed successfully: {}", messageId);
    }
}
