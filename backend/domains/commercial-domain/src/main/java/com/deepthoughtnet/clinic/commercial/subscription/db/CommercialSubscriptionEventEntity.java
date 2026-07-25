package com.deepthoughtnet.clinic.commercial.subscription.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "commercial_subscription_events", indexes = {
        @Index(name = "ix_commercial_subscription_events_subscription", columnList = "subscription_id"),
        @Index(name = "ix_commercial_subscription_events_performed_at", columnList = "subscription_id,performed_at")
})
public class CommercialSubscriptionEventEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private CommercialTenantSubscriptionEntity subscription;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "previous_status", length = 32)
    private String previousStatus;

    @Column(name = "new_status", nullable = false, length = 32)
    private String newStatus;

    @Column(name = "performed_by")
    private UUID performedBy;

    @Column(name = "performed_at", nullable = false)
    private OffsetDateTime performedAt;

    @Column(name = "remarks", length = 1000)
    private String remarks;

    protected CommercialSubscriptionEventEntity() {
    }

    public static CommercialSubscriptionEventEntity create(
            UUID id,
            CommercialTenantSubscriptionEntity subscription,
            String eventType,
            String previousStatus,
            String newStatus,
            UUID performedBy,
            OffsetDateTime performedAt,
            String remarks
    ) {
        CommercialSubscriptionEventEntity entity = new CommercialSubscriptionEventEntity();
        entity.id = id;
        entity.subscription = subscription;
        entity.eventType = eventType;
        entity.previousStatus = previousStatus;
        entity.newStatus = newStatus;
        entity.performedBy = performedBy;
        entity.performedAt = performedAt;
        entity.remarks = remarks;
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public CommercialTenantSubscriptionEntity getSubscription() {
        return subscription;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPreviousStatus() {
        return previousStatus;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public UUID getPerformedBy() {
        return performedBy;
    }

    public OffsetDateTime getPerformedAt() {
        return performedAt;
    }

    public String getRemarks() {
        return remarks;
    }
}
