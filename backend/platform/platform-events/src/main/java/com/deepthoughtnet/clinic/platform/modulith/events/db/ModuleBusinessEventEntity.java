package com.deepthoughtnet.clinic.platform.modulith.events.db;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEvent;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "module_business_events",
        indexes = {
                @Index(name = "ix_module_business_events_tenant_status", columnList = "tenant_id,status"),
                @Index(name = "ix_module_business_events_tenant_event_type", columnList = "tenant_id,event_type"),
                @Index(name = "ix_module_business_events_tenant_occurred_at", columnList = "tenant_id,occurred_at"),
                @Index(name = "ix_module_business_events_aggregate", columnList = "aggregate_type,aggregate_id")
        }
)
public class ModuleBusinessEventEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private int eventVersion;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "source_module", nullable = false, length = 64)
    private String sourceModule;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "correlation_id", nullable = false, length = 128)
    private String correlationId;

    @Column(name = "causation_id", nullable = false, length = 128)
    private String causationId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "payload_json", nullable = false, columnDefinition = "text")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ModuleBusinessEventStatus status;

    @Column(name = "listener_count", nullable = false)
    private int listenerCount;

    @Column(name = "succeeded_count", nullable = false)
    private int succeededCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "retry_scheduled_count", nullable = false)
    private int retryScheduledCount;

    @Column(name = "dead_lettered_count", nullable = false)
    private int deadLetteredCount;

    @Column(name = "last_processed_at")
    private OffsetDateTime lastProcessedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected ModuleBusinessEventEntity() {
    }

    public static ModuleBusinessEventEntity create(ModuleBusinessEvent event, String payloadJson, int listenerCount) {
        OffsetDateTime now = OffsetDateTime.now();
        ModuleBusinessEventEntity entity = new ModuleBusinessEventEntity();
        entity.id = event.eventId();
        entity.tenantId = event.tenantId();
        entity.eventType = event.eventType();
        entity.eventVersion = event.eventVersion();
        entity.occurredAt = event.occurredAt();
        entity.sourceModule = event.sourceModule();
        entity.aggregateType = event.aggregateType();
        entity.aggregateId = event.aggregateId();
        entity.correlationId = safe(event.correlationId());
        entity.causationId = safe(event.causationId());
        entity.actorId = event.actorId();
        entity.payloadJson = payloadJson;
        entity.listenerCount = Math.max(0, listenerCount);
        entity.succeededCount = 0;
        entity.failedCount = 0;
        entity.retryScheduledCount = 0;
        entity.deadLetteredCount = 0;
        entity.status = entity.listenerCount == 0 ? ModuleBusinessEventStatus.SUCCEEDED : ModuleBusinessEventStatus.PENDING;
        entity.lastProcessedAt = null;
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

    public String getEventType() {
        return eventType;
    }

    public int getEventVersion() {
        return eventVersion;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public String getSourceModule() {
        return sourceModule;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getCausationId() {
        return causationId;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public ModuleBusinessEventStatus getStatus() {
        return status;
    }

    public int getListenerCount() {
        return listenerCount;
    }

    public int getSucceededCount() {
        return succeededCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public int getRetryScheduledCount() {
        return retryScheduledCount;
    }

    public int getDeadLetteredCount() {
        return deadLetteredCount;
    }

    public OffsetDateTime getLastProcessedAt() {
        return lastProcessedAt;
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

    public void recalculateStatus(int succeededCount, int failedCount, int retryScheduledCount, int deadLetteredCount, int processingCount, int pendingCount) {
        this.succeededCount = Math.max(0, succeededCount);
        this.failedCount = Math.max(0, failedCount);
        this.retryScheduledCount = Math.max(0, retryScheduledCount);
        this.deadLetteredCount = Math.max(0, deadLetteredCount);
        if (pendingCount > 0) {
            this.status = ModuleBusinessEventStatus.PENDING;
        } else if (processingCount > 0) {
            this.status = ModuleBusinessEventStatus.PROCESSING;
        } else if (deadLetteredCount > 0) {
            this.status = ModuleBusinessEventStatus.DEAD_LETTERED;
        } else if (retryScheduledCount > 0) {
            this.status = ModuleBusinessEventStatus.RETRY_SCHEDULED;
        } else if (failedCount > 0) {
            this.status = ModuleBusinessEventStatus.FAILED;
        } else {
            this.status = ModuleBusinessEventStatus.SUCCEEDED;
        }
        this.lastProcessedAt = OffsetDateTime.now();
        this.updatedAt = this.lastProcessedAt;
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
