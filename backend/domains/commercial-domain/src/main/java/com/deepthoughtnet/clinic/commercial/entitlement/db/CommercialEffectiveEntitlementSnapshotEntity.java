package com.deepthoughtnet.clinic.commercial.entitlement.db;

import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementEnums.GenerationReason;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementEnums.SnapshotStatus;
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
@Table(name = "commercial_effective_entitlement_snapshots", indexes = {
        @Index(name = "ix_commercial_effective_snapshots_tenant_status", columnList = "tenant_id,snapshot_status"),
        @Index(name = "ix_commercial_effective_snapshots_subscription", columnList = "subscription_id"),
        @Index(name = "ix_commercial_effective_snapshots_version", columnList = "published_version_id"),
        @Index(name = "ix_commercial_effective_snapshots_content_hash", columnList = "content_hash")
})
public class CommercialEffectiveEntitlementSnapshotEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "plan_template_id")
    private UUID planTemplateId;

    @Column(name = "published_version_id")
    private UUID publishedVersionId;

    @Column(name = "published_version_number")
    private Integer publishedVersionNumber;

    @Column(name = "subscription_status", length = 32)
    private String subscriptionStatus;

    @Column(name = "effective_from")
    private OffsetDateTime effectiveFrom;

    @Column(name = "effective_until")
    private OffsetDateTime effectiveUntil;

    @Enumerated(EnumType.STRING)
    @Column(name = "snapshot_status", nullable = false, length = 32)
    private SnapshotStatus snapshotStatus;

    @Column(name = "canonical_snapshot_json", nullable = false, columnDefinition = "jsonb")
    private String canonicalSnapshotJson;

    @Column(name = "source_hash", length = 128)
    private String sourceHash;

    @Column(name = "content_hash", nullable = false, length = 128)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "generation_reason", nullable = false, length = 64)
    private GenerationReason generationReason;

    @Column(name = "validation_state", length = 32)
    private String validationState;

    @Column(name = "validation_findings_json", columnDefinition = "jsonb")
    private String validationFindingsJson;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    @Column(name = "generated_by")
    private String generatedBy;

    @Column(name = "superseded_at")
    private OffsetDateTime supersededAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected CommercialEffectiveEntitlementSnapshotEntity() {
    }

    public static CommercialEffectiveEntitlementSnapshotEntity create(
            UUID id,
            UUID tenantId,
            UUID subscriptionId,
            UUID planTemplateId,
            UUID publishedVersionId,
            Integer publishedVersionNumber,
            String subscriptionStatus,
            OffsetDateTime effectiveFrom,
            OffsetDateTime effectiveUntil,
            SnapshotStatus snapshotStatus,
            String canonicalSnapshotJson,
            String sourceHash,
            String contentHash,
            GenerationReason generationReason,
            String validationState,
            String validationFindingsJson,
            OffsetDateTime generatedAt,
            String generatedBy
    ) {
        CommercialEffectiveEntitlementSnapshotEntity entity = new CommercialEffectiveEntitlementSnapshotEntity();
        entity.id = id;
        entity.tenantId = tenantId;
        entity.subscriptionId = subscriptionId;
        entity.planTemplateId = planTemplateId;
        entity.publishedVersionId = publishedVersionId;
        entity.publishedVersionNumber = publishedVersionNumber;
        entity.subscriptionStatus = subscriptionStatus;
        entity.effectiveFrom = effectiveFrom;
        entity.effectiveUntil = effectiveUntil;
        entity.snapshotStatus = snapshotStatus;
        entity.canonicalSnapshotJson = canonicalSnapshotJson;
        entity.sourceHash = sourceHash;
        entity.contentHash = contentHash;
        entity.generationReason = generationReason;
        entity.validationState = validationState;
        entity.validationFindingsJson = validationFindingsJson;
        entity.generatedAt = generatedAt;
        entity.generatedBy = generatedBy;
        entity.version = 0L;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getSubscriptionId() { return subscriptionId; }
    public UUID getPlanTemplateId() { return planTemplateId; }
    public UUID getPublishedVersionId() { return publishedVersionId; }
    public Integer getPublishedVersionNumber() { return publishedVersionNumber; }
    public String getSubscriptionStatus() { return subscriptionStatus; }
    public OffsetDateTime getEffectiveFrom() { return effectiveFrom; }
    public OffsetDateTime getEffectiveUntil() { return effectiveUntil; }
    public SnapshotStatus getSnapshotStatus() { return snapshotStatus; }
    public String getCanonicalSnapshotJson() { return canonicalSnapshotJson; }
    public String getSourceHash() { return sourceHash; }
    public String getContentHash() { return contentHash; }
    public GenerationReason getGenerationReason() { return generationReason; }
    public String getValidationState() { return validationState; }
    public String getValidationFindingsJson() { return validationFindingsJson; }
    public OffsetDateTime getGeneratedAt() { return generatedAt; }
    public String getGeneratedBy() { return generatedBy; }
    public OffsetDateTime getSupersededAt() { return supersededAt; }
    public long getVersion() { return version; }

    public void supersede(OffsetDateTime now) {
        this.snapshotStatus = SnapshotStatus.SUPERSEDED;
        this.supersededAt = now;
    }

    public void markInvalid(String validationState, String validationFindingsJson, OffsetDateTime now) {
        this.snapshotStatus = SnapshotStatus.INVALID;
        this.validationState = validationState;
        this.validationFindingsJson = validationFindingsJson;
        this.supersededAt = null;
        this.generatedAt = now;
    }
}
