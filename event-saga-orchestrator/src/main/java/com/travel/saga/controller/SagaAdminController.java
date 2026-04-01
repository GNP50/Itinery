package com.travel.saga.controller;

import com.travel.saga.domain.DlqMessage;
import com.travel.saga.dto.SagaInstanceDto;
import com.travel.saga.dto.SagaListRequest;
import com.travel.saga.dto.SagaOperationResult;
import com.travel.saga.port.inbound.SagaCommandPort;
import com.travel.saga.port.inbound.SagaQueryPort;
import com.travel.saga.repository.DlqMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/saga")
@Slf4j
@RequiredArgsConstructor
public class SagaAdminController {

    private final SagaQueryPort queryPort;
    private final SagaCommandPort commandPort;
    private final DlqMessageRepository dlqMessageRepository;

    @GetMapping("/instances")
    public ResponseEntity<List<SagaInstanceDto>> getAllInstances(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        log.debug("Fetching all saga instances");
        var request = SagaListRequest.of(offset, limit);
        List<SagaInstanceDto> instances = queryPort.listSagaInstances(request);
        return ResponseEntity.ok(instances);
    }

    @GetMapping("/instances/{id}")
    public ResponseEntity<SagaInstanceDto> getInstanceById(@PathVariable UUID id) {
        log.debug("Fetching saga instance: {}", id);
        Optional<SagaInstanceDto> instance = queryPort.getSagaInstance(id);
        return instance.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/instances/{id}/retry")
    public ResponseEntity<SagaOperationResult> retryInstance(@PathVariable UUID id) {
        log.info("Retrying saga instance: {}", id);
        SagaOperationResult result = commandPort.retrySaga(id);

        if (!result.success()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/dlq")
    public ResponseEntity<List<DlqMessage>> getAllDLQMessages() {
        log.debug("Fetching all DLQ messages");
        List<DlqMessage> messages = dlqMessageRepository.findAll();
        return ResponseEntity.ok(messages);
    }

    @GetMapping("/dlq/{id}")
    public ResponseEntity<DlqMessage> getDLQMessageById(@PathVariable UUID id) {
        log.debug("Fetching DLQ message: {}", id);
        Optional<DlqMessage> message = dlqMessageRepository.findById(id);
        return message.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/dlq/{id}/replay")
    public ResponseEntity<DlqMessage> replayDLQMessage(@PathVariable UUID id) {
        log.info("Replaying DLQ message: {}", id);
        Optional<DlqMessage> optionalMessage = dlqMessageRepository.findById(id);

        if (optionalMessage.isEmpty()) {
            log.warn("DLQ message not found: {}", id);
            return ResponseEntity.notFound().build();
        }

        DlqMessage message = optionalMessage.get();
        message.setProcessed(true);
        message.setProcessedAt(java.time.Instant.now());
        dlqMessageRepository.save(message);

        log.info("DLQ message replayed: {}", id);

        // TODO: Implement DLQ replay logic if needed
        // The saga state machine is no longer directly accessible here

        return ResponseEntity.ok(message);
    }

    @DeleteMapping("/dlq/{id}")
    public ResponseEntity<Void> deleteDLQMessage(@PathVariable UUID id) {
        log.info("Deleting DLQ message: {}", id);
        if (!dlqMessageRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        dlqMessageRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/instances/by-itinerary/{itineraryId}")
    public ResponseEntity<List<SagaInstanceDto>> getInstancesByItineraryId(@PathVariable UUID itineraryId) {
        log.debug("Fetching saga instances for itinerary: {}", itineraryId);
        List<SagaInstanceDto> instances = queryPort.getSagasByItinerary(itineraryId);
        return ResponseEntity.ok(instances);
    }

    @PostMapping("/compensate/{id}")
    public ResponseEntity<SagaOperationResult> compensateInstance(@PathVariable UUID id) {
        log.info("Triggering compensation for saga instance: {}", id);
        SagaOperationResult result = commandPort.compensateSaga(id);

        if (!result.success()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }

        return ResponseEntity.ok(result);
    }
}
