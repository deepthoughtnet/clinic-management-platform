package com.deepthoughtnet.clinic.discover.landingpage.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "discover_landing_page_versions")
public class LandingPageVersionEntity {
    @Id
    private UUID id;

    @Column(name = "provider_id", nullable = false)
    private UUID providerId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "template_key", nullable = false, length = 64)
    private String templateKey;

    @Column(name = "template_version", nullable = false)
    private int templateVersion;

    @Column(name = "version_kind", nullable = false, length = 32)
    private String versionKind;

    @Column(name = "change_summary", nullable = false, length = 1000)
    private String changeSummary;

    @Column(name = "snapshot_hash", nullable = false, length = 128)
    private String snapshotHash;

    @Column(name = "snapshot_json", nullable = false, columnDefinition = "text")
    private String snapshotJson;

    @Column(name = "published_at", nullable = false)
    private OffsetDateTime publishedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected LandingPageVersionEntity() {
    }

    public static LandingPageVersionEntity create(
            UUID providerId,
            int versionNumber,
            String templateKey,
            int templateVersion,
            String versionKind,
            String changeSummary,
            String snapshotHash,
            String snapshotJson,
            OffsetDateTime publishedAt
    ) {
        LandingPageVersionEntity entity = new LandingPageVersionEntity();
        entity.id = UUID.randomUUID();
        entity.providerId = providerId;
        entity.versionNumber = versionNumber;
        entity.templateKey = templateKey;
        entity.templateVersion = templateVersion;
        entity.versionKind = versionKind;
        entity.changeSummary = changeSummary;
        entity.snapshotHash = snapshotHash;
        entity.snapshotJson = snapshotJson;
        entity.publishedAt = publishedAt;
        entity.createdAt = publishedAt;
        entity.rowVersion = 0L;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getProviderId() { return providerId; }
    public int getVersionNumber() { return versionNumber; }
    public String getTemplateKey() { return templateKey; }
    public int getTemplateVersion() { return templateVersion; }
    public String getVersionKind() { return versionKind; }
    public String getChangeSummary() { return changeSummary; }
    public String getSnapshotHash() { return snapshotHash; }
    public String getSnapshotJson() { return snapshotJson; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public long getRowVersion() { return rowVersion; }
}
