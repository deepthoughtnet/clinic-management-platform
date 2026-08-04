package com.deepthoughtnet.clinic.discover.publicprofilemoderation.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "discover_public_profile_review_findings")
public class DiscoverPublicProfileReviewFindingEntity {
    @Id
    private UUID id;
    @Column(name = "finding_reference", nullable = false, unique = true, length = 120)
    private String findingReference;
    @Column(name = "submission_reference", nullable = false, length = 120)
    private String submissionReference;
    @Column(nullable = false, length = 64)
    private String section;
    @Column(name = "field_key", length = 128)
    private String fieldKey;
    @Column(nullable = false, length = 64)
    private String category;
    @Column(nullable = false, length = 32)
    private String severity;
    @Column(nullable = false)
    private boolean required;
    @Column(name = "reviewer_note", length = 1000)
    private String reviewerNote;
    @Column(name = "resolution_status", nullable = false, length = 32)
    private String resolutionStatus;
    @Column(name = "provider_resolution_note", length = 1000)
    private String providerResolutionNote;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;
    @Version
    @Column(name = "optimistic_lock_version", nullable = false)
    private long optimisticLockVersion;

    protected DiscoverPublicProfileReviewFindingEntity() {
    }

    public static DiscoverPublicProfileReviewFindingEntity create(
            UUID id,
            String findingReference,
            String submissionReference,
            String section,
            String fieldKey,
            String category,
            String severity,
            boolean required,
            String reviewerNote,
            String resolutionStatus,
            OffsetDateTime createdAt
    ) {
        DiscoverPublicProfileReviewFindingEntity entity = new DiscoverPublicProfileReviewFindingEntity();
        entity.id = id;
        entity.findingReference = findingReference;
        entity.submissionReference = submissionReference;
        entity.section = section;
        entity.fieldKey = fieldKey;
        entity.category = category;
        entity.severity = severity;
        entity.required = required;
        entity.reviewerNote = reviewerNote;
        entity.resolutionStatus = resolutionStatus;
        entity.createdAt = createdAt;
        return entity;
    }

    public UUID getId() { return id; }
    public String getFindingReference() { return findingReference; }
    public String getSubmissionReference() { return submissionReference; }
    public String getSection() { return section; }
    public String getFieldKey() { return fieldKey; }
    public String getCategory() { return category; }
    public String getSeverity() { return severity; }
    public boolean isRequired() { return required; }
    public String getReviewerNote() { return reviewerNote; }
    public String getResolutionStatus() { return resolutionStatus; }
    public String getProviderResolutionNote() { return providerResolutionNote; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
}
