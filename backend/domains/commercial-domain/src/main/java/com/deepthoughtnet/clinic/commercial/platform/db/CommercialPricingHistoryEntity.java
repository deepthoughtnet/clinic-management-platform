package com.deepthoughtnet.clinic.commercial.platform.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "commercial_pricing_history", indexes = {
        @Index(name = "ix_commercial_pricing_history_pricing", columnList = "pricing_id"),
        @Index(name = "ix_commercial_pricing_history_version", columnList = "published_version_id")
})
public class CommercialPricingHistoryEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pricing_id", nullable = false)
    private CommercialPlanPricingEntity pricing;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "published_version_id", nullable = false)
    private CommercialPlanVersionEntity publishedVersion;

    @Column(name = "content_hash", nullable = false, length = 128)
    private String contentHash;

    @Column(name = "snapshot_json", nullable = false, columnDefinition = "jsonb")
    private String snapshotJson;

    @Column(name = "change_summary", length = 1000)
    private String changeSummary;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Version
    @Column(nullable = false)
    private long version;

    protected CommercialPricingHistoryEntity() {
    }

    public static CommercialPricingHistoryEntity create(
            UUID id,
            CommercialPlanPricingEntity pricing,
            CommercialPlanVersionEntity publishedVersion,
            String contentHash,
            String snapshotJson,
            String changeSummary,
            OffsetDateTime createdAt,
            UUID createdBy
    ) {
        CommercialPricingHistoryEntity entity = new CommercialPricingHistoryEntity();
        entity.id = id;
        entity.pricing = pricing;
        entity.publishedVersion = publishedVersion;
        entity.contentHash = contentHash;
        entity.snapshotJson = snapshotJson;
        entity.changeSummary = changeSummary;
        entity.createdAt = createdAt;
        entity.createdBy = createdBy;
        entity.version = 0L;
        return entity;
    }

    public UUID getId() { return id; }
    public CommercialPlanPricingEntity getPricing() { return pricing; }
    public CommercialPlanVersionEntity getPublishedVersion() { return publishedVersion; }
    public String getContentHash() { return contentHash; }
    public String getSnapshotJson() { return snapshotJson; }
    public String getChangeSummary() { return changeSummary; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public UUID getCreatedBy() { return createdBy; }
    public long getVersion() { return version; }
}
