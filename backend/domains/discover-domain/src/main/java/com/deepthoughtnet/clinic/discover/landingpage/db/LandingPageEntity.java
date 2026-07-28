package com.deepthoughtnet.clinic.discover.landingpage.db;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "discover_landing_pages")
public class LandingPageEntity {
    @Id
    @Column(name = "provider_id", nullable = false)
    private UUID providerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 32)
    private ProviderType providerType;

    @Column(name = "canonical_slug", nullable = false, length = 256)
    private String canonicalSlug;

    @Column(name = "template_key", nullable = false, length = 64)
    private String templateKey;

    @Column(name = "draft_snapshot_json", nullable = false, columnDefinition = "text")
    private String draftSnapshotJson;

    @Column(name = "published_snapshot_json", columnDefinition = "text")
    private String publishedSnapshotJson;

    @Column(name = "published_version_id")
    private UUID publishedVersionId;

    @Column(name = "published_version_number")
    private Integer publishedVersionNumber;

    @Column(name = "draft_updated_at", nullable = false)
    private OffsetDateTime draftUpdatedAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected LandingPageEntity() {
    }

    public static LandingPageEntity create(UUID providerId, ProviderType providerType, String canonicalSlug, String templateKey, String draftSnapshotJson, String displayRef, String providerName) {
        OffsetDateTime now = OffsetDateTime.now();
        LandingPageEntity entity = new LandingPageEntity();
        entity.providerId = providerId;
        entity.providerType = providerType;
        entity.canonicalSlug = canonicalSlug;
        entity.templateKey = templateKey;
        entity.draftSnapshotJson = draftSnapshotJson;
        entity.publishedSnapshotJson = null;
        entity.publishedVersionId = null;
        entity.publishedVersionNumber = null;
        entity.draftUpdatedAt = now;
        entity.createdAt = now;
        entity.updatedAt = now;
        entity.rowVersion = 0L;
        return entity;
    }

    public void updateDraft(String templateKey, String draftSnapshotJson, UUID providerId, String referenceNumber, ProviderType providerType, String displayName) {
        this.templateKey = templateKey;
        this.draftSnapshotJson = draftSnapshotJson;
        this.draftUpdatedAt = OffsetDateTime.now();
        this.updatedAt = this.draftUpdatedAt;
    }

    public void publish(UUID versionId, int versionNumber, String publishedSnapshotJson, ProviderType providerType, String displayName) {
        this.publishedVersionId = versionId;
        this.publishedVersionNumber = versionNumber;
        this.publishedSnapshotJson = publishedSnapshotJson;
        this.publishedAt = OffsetDateTime.now();
        this.updatedAt = this.publishedAt;
    }

    public UUID getProviderId() { return providerId; }
    public ProviderType getProviderType() { return providerType; }
    public String getCanonicalSlug() { return canonicalSlug; }
    public String getTemplateKey() { return templateKey; }
    public String getDraftSnapshotJson() { return draftSnapshotJson; }
    public String getPublishedSnapshotJson() { return publishedSnapshotJson; }
    public UUID getPublishedVersionId() { return publishedVersionId; }
    public Integer getPublishedVersionNumber() { return publishedVersionNumber; }
    public OffsetDateTime getDraftUpdatedAt() { return draftUpdatedAt; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public long getRowVersion() { return rowVersion; }
}
