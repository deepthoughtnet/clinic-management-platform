package com.deepthoughtnet.clinic.notificationcenter.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.NotificationCategory;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.NotificationPriority;

@Entity
@Table(name = "staff_notification_recipients", indexes = {
        @Index(name = "ix_staff_notification_recipients_tenant_user_created", columnList = "tenant_id,app_user_id,created_at"),
        @Index(name = "ix_staff_notification_recipients_tenant_user_read", columnList = "tenant_id,app_user_id,read_at"),
        @Index(name = "ix_staff_notification_recipients_notification", columnList = "staff_notification_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_staff_notification_recipient", columnNames = {"tenant_id", "staff_notification_id", "app_user_id"})
})
public class StaffNotificationRecipientEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_notification_id", nullable = false)
    private StaffNotificationEntity notification;

    @Column(name = "staff_notification_id", nullable = false, insertable = false, updatable = false)
    private UUID notificationId;

    @Column(name = "app_user_id", nullable = false)
    private UUID appUserId;

    @Column(name = "recipient_display_name", length = 256)
    private String recipientDisplayName;

    @Column(name = "recipient_role", length = 80)
    private String recipientRole;

    @Column(name = "matched_audience", nullable = false, length = 240)
    private String matchedAudience;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "preview", nullable = false, columnDefinition = "text")
    private String preview;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private NotificationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 16)
    private NotificationPriority priority;

    @Column(name = "business_reference", length = 160)
    private String businessReference;

    @Column(name = "action_label", length = 120)
    private String actionLabel;

    @Column(name = "action_route", length = 120)
    private String actionRoute;

    @Column(name = "action_target_id")
    private UUID actionTargetId;

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

    @Column(name = "correlation_id", length = 160)
    private String correlationId;

    @Column(name = "causation_id", length = 160)
    private String causationId;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected StaffNotificationRecipientEntity() {
    }

    public static StaffNotificationRecipientEntity create(
            UUID tenantId,
            StaffNotificationEntity notification,
            UUID appUserId,
            String recipientDisplayName,
            String recipientRole,
            String matchedAudience,
            String title,
            String preview,
            NotificationCategory category,
            NotificationPriority priority,
            String businessReference,
            String actionLabel,
            String actionRoute,
            UUID actionTargetId,
            UUID sourceEventId,
            String sourceEventType,
            String sourceModule,
            String aggregateType,
            UUID aggregateId,
            String correlationId,
            String causationId,
            OffsetDateTime occurredAt
    ) {
        StaffNotificationRecipientEntity entity = new StaffNotificationRecipientEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.notification = notification;
        entity.appUserId = appUserId;
        entity.recipientDisplayName = recipientDisplayName;
        entity.recipientRole = recipientRole;
        entity.matchedAudience = matchedAudience;
        entity.title = title;
        entity.preview = preview;
        entity.category = category;
        entity.priority = priority;
        entity.businessReference = businessReference;
        entity.actionLabel = actionLabel;
        entity.actionRoute = actionRoute;
        entity.actionTargetId = actionTargetId;
        entity.sourceEventId = sourceEventId;
        entity.sourceEventType = sourceEventType;
        entity.sourceModule = sourceModule;
        entity.aggregateType = aggregateType;
        entity.aggregateId = aggregateId;
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

    public StaffNotificationEntity getNotification() {
        return notification;
    }

    public UUID getNotificationId() {
        return notificationId;
    }

    public UUID getAppUserId() {
        return appUserId;
    }

    public String getRecipientDisplayName() {
        return recipientDisplayName;
    }

    public String getRecipientRole() {
        return recipientRole;
    }

    public String getMatchedAudience() {
        return matchedAudience;
    }

    public String getTitle() {
        return title;
    }

    public String getPreview() {
        return preview;
    }

    public NotificationCategory getCategory() {
        return category;
    }

    public NotificationPriority getPriority() {
        return priority;
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

    public String getCorrelationId() {
        return correlationId;
    }

    public String getCausationId() {
        return causationId;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public OffsetDateTime getReadAt() {
        return readAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public boolean isRead() {
        return readAt != null;
    }

    public void markRead() {
        this.readAt = OffsetDateTime.now();
        this.updatedAt = this.readAt;
    }

    public void markUnread() {
        this.readAt = null;
        this.updatedAt = OffsetDateTime.now();
    }
}
