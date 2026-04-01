package com.travel.saga.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "saga_instances")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SagaInstance {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "itinerary_id", nullable = false)
    private UUID itineraryId;

    @Column(name = "current_state", length = 50, nullable = false)
    private String currentState;

    @ElementCollection
    @CollectionTable(
        name = "saga_completed_steps",
        joinColumns = @JoinColumn(name = "saga_instance_id")
    )
    @Column(name = "step")
    private Set<String> completedSteps = new HashSet<>();

    @Column(name = "failed_step", length = 50)
    @Setter
    private String failedStep;

    @Column(name = "error_message", columnDefinition = "TEXT")
    @Setter
    private String errorMessage;

    @Version
    @Column(name = "version")
    private long version;

    @Column(name = "retry_count", nullable = false)
    @Setter
    private int retryCount;

    @Column(name = "max_retries", nullable = false)
    @Setter
    private int maxRetries = 3;

    /**
     * Priority level for queue processing.
     * Higher values indicate higher priority (e.g., REGISTERED=1, ANONYMOUS=0).
     */
    @Column(name = "priority", nullable = false)
    @Setter
    private int priority = 0;

    /**
     * User type that created this saga (REGISTERED, ADMIN, ANONYMOUS).
     * Used for analytics and priority-based queue processing.
     */
    @Column(name = "user_type", length = 20)
    @Setter
    private String userType;

    /**
     * JSON blob of user preferences forwarded from {@code ItineraryCreatedEvent}.
     * Passed to worker requests so the AI/route workers can personalise processing.
     */
    @Column(name = "preferences", columnDefinition = "TEXT")
    @Setter
    private String preferences;

    /**
     * Saga version counter incremented on each saga restart.
     * Used to invalidate stale worker responses after itinerary updates.
     */
    @Column(name = "saga_version", nullable = false)
    @Setter
    private long sagaVersion = 0;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    public SagaInstance(UUID id, UUID itineraryId, String initialState) {
        this.id = id;
        this.itineraryId = itineraryId;
        this.currentState = initialState;
        this.retryCount = 0;
    }

    public static SagaInstance create(UUID itineraryId, String userType, String preferencesJson) {
        SagaInstance saga = new SagaInstance(UUID.randomUUID(), itineraryId, "INITIAL");
        saga.userType = userType;
        saga.priority = calculatePriority(userType);
        saga.preferences = preferencesJson;
        return saga;
    }

    /**
     * Calculate priority based on user type.
     * Higher priority values are processed first.
     */
    private static int calculatePriority(String userType) {
        if ("ADMIN".equalsIgnoreCase(userType)) {
            return 2;
        } else if ("REGISTERED".equalsIgnoreCase(userType)) {
            return 1;
        } else {
            return 0; // ANONYMOUS or unknown
        }
    }

    @PrePersist
    protected void prePersist() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public void updateState(String newState) {
        this.currentState = newState;
    }

    public void addCompletedStep(String stepName) {
        this.completedSteps.add(stepName);
    }

    public void removeCompletedStep(String stepName) {
        this.completedSteps.remove(stepName);
    }

    public boolean isCompletedStep(String stepName) {
        return this.completedSteps.contains(stepName);
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    public boolean canRetry() {
        return this.retryCount < this.maxRetries;
    }

    public void resetForRetry() {
        this.retryCount = 0;
        this.failedStep = null;
        this.errorMessage = null;
    }

    /**
     * Recalculate priority based on current user type.
     * Should be called after updating the user type.
     */
    public void recalculatePriority() {
        this.priority = calculatePriority(this.userType);
    }

    /**
     * Increment saga version counter.
     * Should be called when restarting a saga due to itinerary update.
     */
    public void incrementSagaVersion() {
        this.sagaVersion++;
    }
}
