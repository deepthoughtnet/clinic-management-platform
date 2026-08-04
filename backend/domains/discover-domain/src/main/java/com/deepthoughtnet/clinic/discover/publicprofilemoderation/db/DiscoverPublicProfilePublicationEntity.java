package com.deepthoughtnet.clinic.discover.publicprofilemoderation.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "discover_public_profile_publications")
public class DiscoverPublicProfilePublicationEntity {
    @Id
    private UUID id;
    @Column(name = "publication_reference", nullable = false, unique = true, length = 120)
    private String publicationReference;
    @Column(name = "public_profile_reference", nullable = false, length = 160)
    private String publicProfileReference;
    @Column(name = "approved_submission_reference", nullable = false, length = 120)
    private String approvedSubmissionReference;
    @Column(name = "published_version", nullable = false)
    private int publishedVersion;
    @Column(name = "publication_status", nullable = false, length = 32)
    private String publicationStatus;
    @Column(nullable = false, length = 256)
    private String slug;
    @Column(name = "public_path", nullable = false, length = 256)
    private String publicPath;
    @Column(length = 1000)
    private String reason;
    @Column(name = "published_at", nullable = false)
    private OffsetDateTime publishedAt;
    @Column(name = "unpublished_at")
    private OffsetDateTime unpublishedAt;
    @Column(name = "current_flag", nullable = false)
    private boolean current;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    @Version
    @Column(name = "optimistic_lock_version", nullable = false)
    private long optimisticLockVersion;

    protected DiscoverPublicProfilePublicationEntity() {
    }

    public static DiscoverPublicProfilePublicationEntity create(
            UUID id,
            String publicationReference,
            String publicProfileReference,
            String approvedSubmissionReference,
            int publishedVersion,
            String publicationStatus,
            String slug,
            String publicPath,
            String reason,
            OffsetDateTime publishedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        DiscoverPublicProfilePublicationEntity entity = new DiscoverPublicProfilePublicationEntity();
        entity.id = id;
        entity.publicationReference = publicationReference;
        entity.publicProfileReference = publicProfileReference;
        entity.approvedSubmissionReference = approvedSubmissionReference;
        entity.publishedVersion = publishedVersion;
        entity.publicationStatus = publicationStatus;
        entity.slug = slug;
        entity.publicPath = publicPath;
        entity.reason = reason;
        entity.publishedAt = publishedAt;
        entity.createdAt = createdAt;
        entity.updatedAt = updatedAt;
        entity.current = true;
        return entity;
    }

    public void unpublish(String reason, OffsetDateTime unpublishedAt, OffsetDateTime updatedAt) {
        this.publicationStatus = "UNPUBLISHED";
        this.reason = reason;
        this.unpublishedAt = unpublishedAt;
        this.updatedAt = updatedAt;
        this.current = false;
    }

    public UUID getId() { return id; }
    public String getPublicationReference() { return publicationReference; }
    public String getPublicProfileReference() { return publicProfileReference; }
    public String getApprovedSubmissionReference() { return approvedSubmissionReference; }
    public int getPublishedVersion() { return publishedVersion; }
    public String getPublicationStatus() { return publicationStatus; }
    public String getSlug() { return slug; }
    public String getPublicPath() { return publicPath; }
    public String getReason() { return reason; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public OffsetDateTime getUnpublishedAt() { return unpublishedAt; }
    public boolean isCurrent() { return current; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
