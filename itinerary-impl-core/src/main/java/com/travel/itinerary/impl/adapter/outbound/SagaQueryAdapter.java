package com.travel.itinerary.impl.adapter.outbound;

import com.travel.itinerary.api.port.outbound.SagaQueryPort;
import com.travel.saga.client.SagaGrpcClient;
import com.travel.saga.dto.SagaInstanceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter that implements the SagaQueryPort using the saga gRPC client.
 * <p>
 * This allows the itinerary-impl-core module to query saga orchestration
 * state without directly depending on the gRPC client implementation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SagaQueryAdapter implements SagaQueryPort {

    private final SagaGrpcClient sagaGrpcClient;

    @Override
    public List<SagaInstanceDto> getSagasByItinerary(UUID itineraryId) {
        log.debug("SagaQueryAdapter: getting sagas for itinerary {}", itineraryId);
        return sagaGrpcClient.getSagasByItinerary(itineraryId);
    }

    @Override
    public Optional<SagaInstanceDto> getSagaInstance(UUID sagaId) {
        log.debug("SagaQueryAdapter: getting saga instance {}", sagaId);
        return sagaGrpcClient.getSagaInstance(sagaId);
    }
}

