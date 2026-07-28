package com.deepthoughtnet.clinic.discover.publicprofile.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "discover_public_provider_profile_versions")
public class DiscoverPublicProviderProfileVersionEntity {
    @Id
    private UUID id;

    @Column(name = "provider_id", nullable = false)
    private UUID providerId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "source_submission_version_number", nullable = false)
    private int sourceSubmissionVersionNumber;

    @Column(name = "status_before", length = 32)
    private String statusBefore;

    @Column(name = "status_after", nullable = false, length = 32)
    private String statusAfter;

    @Column(name = "published_by", length = 64)
    private String publishedBy;

    @Column(name = "publication_reason", length = 1000)
    private String publicationReason;

    @Column(name = "snapshot_hash", nullable = false, length = 128)
    private String snapshotHash;

    @Column(name = "snapshot_json", nullable = false, columnDefinition = "text")
    private String snapshotJson;

    @Column(name = "canonical_slug", nullable = false, length = 256)
    private String canonicalSlug;

    @Column(name = "published_at", nullable = false)
    private OffsetDateTime publishedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected DiscoverPublicProviderProfileVersionEntity() {
    }

    public static DiscoverPublicProviderProfileVersionEntity create(
            UUID providerId,
            int versionNumber,
            int sourceSubmissionVersionNumber,
            String statusBefore,
            String statusAfter,
            String publishedBy,
            String publicationReason,
            String snapshotHash,
            String snapshotJson,
            String canonicalSlug,
            OffsetDateTime publishedAt
    ) {
        DiscoverPublicProviderProfileVersionEntity entity = new DiscoverPublicProviderProfileVersionEntity();
        entity.id = UUID.randomUUID();
        entity.providerId = providerId;
        entity.versionNumber = versionNumber;
        entity.sourceSubmissionVersionNumber = sourceSubmissionVersionNumber;
        entity.statusBefore = statusBefore;
        entity.statusAfter = statusAfter;
        entity.publishedBy = publishedBy;
        entity.publicationReason = publicationReason;
        entity.snapshotHash = snapshotHash;
        entity.snapshotJson = snapshotJson;
        entity.canonicalSlug = canonicalSlug;
        entity.publishedAt = publishedAt;
        entity.createdAt = publishedAt;
        entity.rowVersion = 0L;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getProviderId() { return providerId; }
    public int getVersionNumber() { return versionNumber; }
    public int getSourceSubmissionVersionNumber() { return sourceSubmissionVersionNumber; }
    public String getStatusBefore() { return statusBefore; }
    public String getStatusAfter() { return statusAfter; }
    public String getPublishedBy() { return publishedBy; }
    public String getPublicationReason() { return publicationReason; }
    public String getSnapshotHash() { return snapshotHash; }
    public String getSnapshotJson() { return snapshotJson; }
    public String getCanonicalSlug() { return canonicalSlug; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public long getRowVersion() { return rowVersion; }
}
