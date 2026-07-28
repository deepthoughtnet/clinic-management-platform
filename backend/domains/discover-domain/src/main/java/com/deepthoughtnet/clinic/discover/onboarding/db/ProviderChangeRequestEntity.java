package com.deepthoughtnet.clinic.discover.onboarding.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "discover_provider_change_requests")
public class ProviderChangeRequestEntity {
    @Id
    private UUID id;
    @Column(name = "provider_id", nullable = false)
    private UUID providerId;
    @Column(name = "submission_version_number")
    private Integer submissionVersionNumber;
    @Column(name = "requested_sections", length = 512)
    private String requestedSections;
    @Column(name = "reviewer_message", length = 1000)
    private String reviewerMessage;
    @Column(name = "provider_response_note", length = 1000)
    private String providerResponseNote;
    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;
    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    protected ProviderChangeRequestEntity() {
    }

    public ProviderChangeRequestEntity(UUID providerId, Integer submissionVersionNumber, String requestedSections, String reviewerMessage) {
        this.id = UUID.randomUUID();
        this.providerId = providerId;
        this.submissionVersionNumber = submissionVersionNumber;
        this.requestedSections = requestedSections;
        this.reviewerMessage = reviewerMessage;
        this.requestedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getProviderId() { return providerId; }
    public Integer getSubmissionVersionNumber() { return submissionVersionNumber; }
    public String getRequestedSections() { return requestedSections; }
    public String getReviewerMessage() { return reviewerMessage; }
    public String getProviderResponseNote() { return providerResponseNote; }
    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public boolean isResolved() { return resolvedAt != null; }

    public void setProviderResponseNote(String providerResponseNote) {
        this.providerResponseNote = providerResponseNote;
    }

    public void markResolved() {
        this.resolvedAt = OffsetDateTime.now();
    }
}
