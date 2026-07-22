package com.deepthoughtnet.clinic.platform.modulith.events.db;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventListenerStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "module_business_event_listener_jobs",
        indexes = {
                @Index(name = "ix_module_business_event_listener_jobs_tenant_status_next", columnList = "tenant_id,status,next_attempt_at"),
                @Index(name = "ix_module_business_event_listener_jobs_event_id", columnList = "event_id"),
                @Index(name = "ix_module_business_event_listener_jobs_tenant_listener", columnList = "tenant_id,listener_name")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_module_business_event_listener_jobs_event_listener", columnNames = {"event_id", "listener_name"})
        }
)
public class ModuleBusinessEventListenerExecutionEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "listener_name", nullable = false, length = 128)
    private String listenerName;

    @Column(name = "listener_module", nullable = false, length = 64)
    private String listenerModule;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private OffsetDateTime nextAttemptAt;

    @Column(name = "last_attempt_at")
    private OffsetDateTime lastAttemptAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(name = "dead_lettered_at")
    private OffsetDateTime deadLetteredAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected ModuleBusinessEventListenerExecutionEntity() {
    }

    public static ModuleBusinessEventListenerExecutionEntity pending(ModuleBusinessEventEntity event, String listenerName, String listenerModule) {
        OffsetDateTime now = OffsetDateTime.now();
        ModuleBusinessEventListenerExecutionEntity entity = new ModuleBusinessEventListenerExecutionEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = event.getTenantId();
        entity.eventId = event.getId();
        entity.eventType = event.getEventType();
        entity.listenerName = listenerName;
        entity.listenerModule = listenerModule;
        entity.status = ModuleBusinessEventListenerStatus.PENDING.name();
        entity.attemptCount = 0;
        entity.nextAttemptAt = now;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getListenerName() {
        return listenerName;
    }

    public String getListenerModule() {
        return listenerModule;
    }

    public String getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public OffsetDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public OffsetDateTime getLastAttemptAt() {
        return lastAttemptAt;
    }

    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }

    public OffsetDateTime getDeadLetteredAt() {
        return deadLetteredAt;
    }

    public String getLastError() {
        return lastError;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public int getVersion() {
        return version;
    }

    public void markProcessing() {
        OffsetDateTime now = OffsetDateTime.now();
        this.status = ModuleBusinessEventListenerStatus.PROCESSING.name();
        this.attemptCount = this.attemptCount + 1;
        this.lastAttemptAt = now;
        this.lastError = null;
        this.updatedAt = now;
    }

    public void markSucceeded() {
        OffsetDateTime now = OffsetDateTime.now();
        this.status = ModuleBusinessEventListenerStatus.SUCCEEDED.name();
        this.processedAt = now;
        this.nextAttemptAt = null;
        this.lastError = null;
        this.updatedAt = now;
    }

    public void markFailed(String error, boolean retryable, int maxAttempts, Duration backoff) {
        OffsetDateTime now = OffsetDateTime.now();
        this.lastError = error;
        if (!retryable) {
            this.status = ModuleBusinessEventListenerStatus.FAILED.name();
            this.nextAttemptAt = null;
            this.updatedAt = now;
            return;
        }
        if (attemptCount >= maxAttempts) {
            this.status = ModuleBusinessEventListenerStatus.DEAD_LETTERED.name();
            this.nextAttemptAt = null;
            this.deadLetteredAt = now;
        } else {
            this.status = ModuleBusinessEventListenerStatus.RETRY_SCHEDULED.name();
            this.nextAttemptAt = now.plus(backoff.multipliedBy(Math.max(1, attemptCount)));
        }
        this.updatedAt = now;
    }

    public void reclaimForRetry() {
        OffsetDateTime now = OffsetDateTime.now();
        this.status = ModuleBusinessEventListenerStatus.PENDING.name();
        this.nextAttemptAt = now;
        this.lastError = null;
        this.updatedAt = now;
    }
}
