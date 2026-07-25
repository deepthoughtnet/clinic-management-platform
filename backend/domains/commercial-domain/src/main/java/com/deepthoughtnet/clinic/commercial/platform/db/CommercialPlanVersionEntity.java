package com.deepthoughtnet.clinic.commercial.platform.db;

import com.deepthoughtnet.clinic.commercial.platform.CommercialPlatformEnums.PublicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "commercial_plan_versions")
public class CommercialPlanVersionEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private CommercialPlanTemplateEntity template;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "version_label", nullable = false, length = 64)
    private String versionLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PublicationStatus status;

    @Column(name = "published_at", nullable = false)
    private OffsetDateTime publishedAt;

    @Column(name = "published_by")
    private UUID publishedBy;

    @Column(name = "publication_notes", length = 1000)
    private String publicationNotes;

    @Column(name = "source_draft_revision", nullable = false)
    private int sourceDraftRevision;

    @Column(name = "content_hash", nullable = false, length = 128)
    private String contentHash;

    @Column(name = "snapshot_json", nullable = false, columnDefinition = "text")
    private String snapshotJson;

    @Column(name = "capability_count", nullable = false)
    private int capabilityCount;

    @Column(name = "module_count", nullable = false)
    private int moduleCount;

    @Column(name = "feature_count", nullable = false)
    private int featureCount;

    @Column(name = "limit_count", nullable = false)
    private int limitCount;

    @Column(name = "addon_count", nullable = false)
    private int addonCount;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Version
    @Column(nullable = false)
    private long version;

    public CommercialPlanVersionEntity() {
    }

    public static CommercialPlanVersionEntity create(CommercialPlanTemplateEntity template, int versionNumber, String versionLabel, PublicationStatus status, OffsetDateTime publishedAt, UUID publishedBy, String publicationNotes, int sourceDraftRevision, String contentHash, String snapshotJson, int capabilityCount, int moduleCount, int featureCount, int limitCount, int addonCount, UUID actor) {
        CommercialPlanVersionEntity entity = new CommercialPlanVersionEntity();
        entity.id = UUID.randomUUID();
        entity.template = template;
        entity.versionNumber = versionNumber;
        entity.versionLabel = versionLabel;
        entity.status = status;
        entity.publishedAt = publishedAt;
        entity.publishedBy = publishedBy;
        entity.publicationNotes = publicationNotes;
        entity.sourceDraftRevision = sourceDraftRevision;
        entity.contentHash = contentHash;
        entity.snapshotJson = snapshotJson;
        entity.capabilityCount = capabilityCount;
        entity.moduleCount = moduleCount;
        entity.featureCount = featureCount;
        entity.limitCount = limitCount;
        entity.addonCount = addonCount;
        entity.createdAt = publishedAt;
        entity.createdBy = actor;
        entity.version = 0L;
        return entity;
    }

    public UUID getId() { return id; }
    public CommercialPlanTemplateEntity getTemplate() { return template; }
    public int getVersionNumber() { return versionNumber; }
    public String getVersionLabel() { return versionLabel; }
    public PublicationStatus getStatus() { return status; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public UUID getPublishedBy() { return publishedBy; }
    public String getPublicationNotes() { return publicationNotes; }
    public int getSourceDraftRevision() { return sourceDraftRevision; }
    public String getContentHash() { return contentHash; }
    public String getSnapshotJson() { return snapshotJson; }
    public int getCapabilityCount() { return capabilityCount; }
    public int getModuleCount() { return moduleCount; }
    public int getFeatureCount() { return featureCount; }
    public int getLimitCount() { return limitCount; }
    public int getAddonCount() { return addonCount; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public UUID getCreatedBy() { return createdBy; }
    public long getVersion() { return version; }
}
