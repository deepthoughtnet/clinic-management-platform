package com.deepthoughtnet.clinic.notificationcenter.db;

import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.NotificationCategory;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.NotificationPriority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "staff_notifications", indexes = {
        @Index(name = "ix_staff_notifications_tenant_created", columnList = "tenant_id,created_at"),
        @Index(name = "ix_staff_notifications_tenant_category", columnList = "tenant_id,category,created_at"),
        @Index(name = "ix_staff_notifications_tenant_priority", columnList = "tenant_id,priority,created_at"),
        @Index(name = "ix_staff_notifications_tenant_event", columnList = "tenant_id,source_event_type")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_staff_notifications_tenant_event", columnNames = {"tenant_id", "source_event_id"})
})
public class StaffNotificationEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "source_event_id", nullable = false)
    private UUID sourceEventId;

    @Column(name = "source_event_type", nullable = false, length = 120)
    private String sourceEventType;

    @Column(name = "source_module", nullable = false, length = 80)
    private String sourceModule;

    @Column(name = "aggregate_type", nullable = false, length = 80)
    private String aggregateType;

    @Column(name = "aggregate_id")
    private UUID aggregateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationPriority priority;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String preview;

    @Column(name = "business_reference", length = 160)
    private String businessReference;

    @Column(name = "action_label", length = 120)
    private String actionLabel;

    @Column(name = "action_route", length = 120)
    private String actionRoute;

    @Column(name = "action_target_id")
    private UUID actionTargetId;

    @Column(name = "correlation_id", length = 160)
    private String correlationId;

    @Column(name = "causation_id", length = 160)
    private String causationId;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected StaffNotificationEntity() {
    }

    public static StaffNotificationEntity create(
            UUID tenantId,
            UUID sourceEventId,
            String sourceEventType,
            String sourceModule,
            String aggregateType,
            UUID aggregateId,
            NotificationCategory category,
            NotificationPriority priority,
            String title,
            String preview,
            String businessReference,
            String actionLabel,
            String actionRoute,
            UUID actionTargetId,
            String correlationId,
            String causationId,
            OffsetDateTime occurredAt
    ) {
        StaffNotificationEntity entity = new StaffNotificationEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.sourceEventId = sourceEventId;
        entity.sourceEventType = sourceEventType;
        entity.sourceModule = sourceModule;
        entity.aggregateType = aggregateType;
        entity.aggregateId = aggregateId;
        entity.category = category;
        entity.priority = priority;
        entity.title = title;
        entity.preview = preview;
        entity.businessReference = businessReference;
        entity.actionLabel = actionLabel;
        entity.actionRoute = actionRoute;
        entity.actionTargetId = actionTargetId;
        entity.correlationId = correlationId;
        entity.causationId = causationId;
        entity.occurredAt = occurredAt;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getSourceEventId() {
        return sourceEventId;
    }

    public String getSourceEventType() {
        return sourceEventType;
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

    public NotificationCategory getCategory() {
        return category;
    }

    public NotificationPriority getPriority() {
        return priority;
    }

    public String getTitle() {
        return title;
    }

    public String getPreview() {
        return preview;
    }

    public String getBusinessReference() {
        return businessReference;
    }

    public String getActionLabel() {
        return actionLabel;
    }

    public String getActionRoute() {
        return actionRoute;
    }

    public UUID getActionTargetId() {
        return actionTargetId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getCausationId() {
        return causationId;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
