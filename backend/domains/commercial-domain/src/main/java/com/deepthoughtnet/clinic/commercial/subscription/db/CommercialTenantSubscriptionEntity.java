package com.deepthoughtnet.clinic.commercial.subscription.db;

import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanTemplateEntity;
import com.deepthoughtnet.clinic.commercial.platform.db.CommercialPlanVersionEntity;
import com.deepthoughtnet.clinic.commercial.subscription.CommercialSubscriptionEnums.SubscriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "commercial_tenant_subscriptions", indexes = {
        @Index(name = "ix_commercial_tenant_subscriptions_tenant", columnList = "tenant_id"),
        @Index(name = "ix_commercial_tenant_subscriptions_published_version", columnList = "published_version_id"),
        @Index(name = "ix_commercial_tenant_subscriptions_status", columnList = "subscription_status"),
        @Index(name = "ix_commercial_tenant_subscriptions_history", columnList = "tenant_id,created_at")
})
public class CommercialTenantSubscriptionEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_template_id", nullable = false)
    private CommercialPlanTemplateEntity planTemplate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "published_version_id", nullable = false)
    private CommercialPlanVersionEntity publishedVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", nullable = false, length = 32)
    private SubscriptionStatus subscriptionStatus;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew;

    @Column(name = "display_name", length = 160)
    private String displayName;

    @Column(name = "reference_number", length = 64)
    private String referenceNumber;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(nullable = false)
    private long version;

    protected CommercialTenantSubscriptionEntity() {
    }

    public static CommercialTenantSubscriptionEntity create(
            UUID id,
            UUID tenantId,
            CommercialPlanTemplateEntity planTemplate,
            CommercialPlanVersionEntity publishedVersion,
            SubscriptionStatus subscriptionStatus,
            LocalDate startDate,
            LocalDate endDate,
            boolean autoRenew,
            String displayName,
            String referenceNumber,
            String notes,
            OffsetDateTime now,
            UUID actor
    ) {
        CommercialTenantSubscriptionEntity entity = new CommercialTenantSubscriptionEntity();
        entity.id = id;
        entity.tenantId = tenantId;
        entity.planTemplate = planTemplate;
        entity.publishedVersion = publishedVersion;
        entity.subscriptionStatus = subscriptionStatus;
        entity.startDate = startDate;
        entity.endDate = endDate;
        entity.autoRenew = autoRenew;
        entity.displayName = displayName;
        entity.referenceNumber = referenceNumber;
        entity.notes = notes;
        entity.createdAt = now;
        entity.createdBy = actor;
        entity.updatedAt = now;
        entity.updatedBy = actor;
        entity.version = 0L;
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public CommercialPlanTemplateEntity getPlanTemplate() {
        return planTemplate;
    }

    public CommercialPlanVersionEntity getPublishedVersion() {
        return publishedVersion;
    }

    public SubscriptionStatus getSubscriptionStatus() {
        return subscriptionStatus;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public boolean isAutoRenew() {
        return autoRenew;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public String getNotes() {
        return notes;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public long getVersion() {
        return version;
    }

    public void updateMetadata(LocalDate startDate, LocalDate endDate, boolean autoRenew, String displayName, String referenceNumber, String notes, OffsetDateTime now, UUID actor) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.autoRenew = autoRenew;
        this.displayName = displayName;
        this.referenceNumber = referenceNumber;
        this.notes = notes;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public void transition(SubscriptionStatus subscriptionStatus, OffsetDateTime now, UUID actor) {
        this.subscriptionStatus = subscriptionStatus;
        this.updatedAt = now;
        this.updatedBy = actor;
    }
}
