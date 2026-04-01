package com.travel.saga.repository;

import com.travel.saga.domain.DlqMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DlqMessageRepository extends JpaRepository<DlqMessage, UUID> {

    List<DlqMessage> findByProcessed(boolean processed);

    List<DlqMessage> findByTopic(String topic);

    List<DlqMessage> findByTimestampAfter(java.time.Instant timestamp);
}
