package com.deepthoughtnet.clinic.commercial.entitlement.db;

import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementEnums.OverrideOperation;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementEnums.OverrideStatus;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementEnums.OverrideTargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "commercial_tenant_entitlement_overrides", indexes = {
        @Index(name = "ix_commercial_entitlement_overrides_tenant_status", columnList = "tenant_id,status"),
        @Index(name = "ix_commercial_entitlement_overrides_subscription", columnList = "subscription_id"),
        @Index(name = "ix_commercial_entitlement_overrides_target", columnList = "tenant_id,target_type,target_code"),
        @Index(name = "ix_commercial_entitlement_overrides_effective_from", columnList = "tenant_id,effective_from"),
        @Index(name = "ix_commercial_entitlement_overrides_effective_until", columnList = "tenant_id,effective_until")
})
public class CommercialTenantEntitlementOverrideEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 32)
    private OverrideTargetType targetType;

    @Column(name = "target_code", nullable = false, length = 64)
    private String targetCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OverrideOperation operation;

    @Column(length = 256)
    private String value;

    @Column(name = "addon_state", length = 64)
    private String addOnState;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_until")
    private LocalDate effectiveUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OverrideStatus status;

    @Column(length = 1000)
    private String reason;

    @Column(name = "internal_notes", length = 2000)
    private String internalNotes;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "review_remarks", length = 2000)
    private String reviewRemarks;

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

    protected CommercialTenantEntitlementOverrideEntity() {
    }

    public static CommercialTenantEntitlementOverrideEntity create(
            UUID id,
            UUID tenantId,
            UUID subscriptionId,
            OverrideTargetType targetType,
            String targetCode,
            OverrideOperation operation,
            String value,
            String addOnState,
            LocalDate effectiveFrom,
            LocalDate effectiveUntil,
            OverrideStatus status,
            String reason,
            String internalNotes,
            OffsetDateTime now,
            UUID actor
    ) {
        CommercialTenantEntitlementOverrideEntity entity = new CommercialTenantEntitlementOverrideEntity();
        entity.id = id;
        entity.tenantId = tenantId;
        entity.subscriptionId = subscriptionId;
        entity.targetType = targetType;
        entity.targetCode = targetCode;
        entity.operation = operation;
        entity.value = value;
        entity.addOnState = addOnState;
        entity.effectiveFrom = effectiveFrom;
        entity.effectiveUntil = effectiveUntil;
        entity.status = status;
        entity.reason = reason;
        entity.internalNotes = internalNotes;
        entity.submittedAt = null;
        entity.submittedBy = null;
        entity.reviewedAt = null;
        entity.reviewedBy = null;
        entity.reviewRemarks = null;
        entity.createdAt = now;
        entity.createdBy = actor;
        entity.updatedAt = now;
        entity.updatedBy = actor;
        entity.version = 0L;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getSubscriptionId() { return subscriptionId; }
    public OverrideTargetType getTargetType() { return targetType; }
    public String getTargetCode() { return targetCode; }
    public OverrideOperation getOperation() { return operation; }
    public String getValue() { return value; }
    public String getAddOnState() { return addOnState; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveUntil() { return effectiveUntil; }
    public OverrideStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public String getInternalNotes() { return internalNotes; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public UUID getSubmittedBy() { return submittedBy; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public UUID getReviewedBy() { return reviewedBy; }
    public String getReviewRemarks() { return reviewRemarks; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public UUID getCreatedBy() { return createdBy; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public UUID getUpdatedBy() { return updatedBy; }
    public long getVersion() { return version; }

    public void update(
            UUID subscriptionId,
            OverrideTargetType targetType,
            String targetCode,
            OverrideOperation operation,
            String value,
            String addOnState,
            LocalDate effectiveFrom,
            LocalDate effectiveUntil,
            OverrideStatus status,
            String reason,
            String internalNotes,
            OffsetDateTime now,
            UUID actor
    ) {
        this.subscriptionId = subscriptionId;
        this.targetType = targetType;
        this.targetCode = targetCode;
        this.operation = operation;
        this.value = value;
        this.addOnState = addOnState;
        this.effectiveFrom = effectiveFrom;
        this.effectiveUntil = effectiveUntil;
        this.status = status;
        this.reason = reason;
        this.internalNotes = internalNotes;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public void submit(OffsetDateTime now, UUID actor) {
        this.status = OverrideStatus.PENDING_APPROVAL;
        this.submittedAt = now;
        this.submittedBy = actor;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public void withdraw(OffsetDateTime now, UUID actor) {
        this.status = OverrideStatus.DRAFT;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public void requestChanges(OffsetDateTime now, UUID actor, String remarks) {
        this.status = OverrideStatus.CHANGES_REQUESTED;
        this.reviewedAt = now;
        this.reviewedBy = actor;
        this.reviewRemarks = remarks;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public void approve(OffsetDateTime now, UUID actor, String remarks) {
        this.status = OverrideStatus.APPROVED;
        this.reviewedAt = now;
        this.reviewedBy = actor;
        this.reviewRemarks = remarks;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public void transition(OverrideStatus status, OffsetDateTime now, UUID actor) {
        this.status = status;
        this.updatedAt = now;
        this.updatedBy = actor;
    }

    public void supersede(OffsetDateTime now, UUID actor) {
        this.status = OverrideStatus.SUPERSEDED;
        this.updatedAt = now;
        this.updatedBy = actor;
    }
}
