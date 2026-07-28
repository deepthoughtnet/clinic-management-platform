package com.deepthoughtnet.clinic.discover.onboarding.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "discover_provider_submissions")
public class ProviderSubmissionEntity {
    @Id
    private UUID id;
    @Column(name = "provider_id", nullable = false)
    private UUID providerId;
    @Column(name = "version_number", nullable = false)
    private int versionNumber;
    @Column(name = "status_before", length = 32)
    private String statusBefore;
    @Column(name = "status_after", nullable = false, length = 32)
    private String statusAfter;
    @Column(name = "submitted_by", nullable = false, length = 64)
    private String submittedBy;
    @Column(name = "snapshot_hash", nullable = false, length = 128)
    private String snapshotHash;
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "text")
    private String snapshotJson;
    @Column(name = "submission_note", length = 512)
    private String submissionNote;
    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt;

    protected ProviderSubmissionEntity() {
    }

    public ProviderSubmissionEntity(UUID providerId, int versionNumber) {
        this(providerId, versionNumber, null, "SUBMITTED", "PROVIDER", "", "{}", null);
    }

    public ProviderSubmissionEntity(UUID providerId, int versionNumber, String statusBefore, String statusAfter, String submittedBy, String snapshotHash, String snapshotJson, String submissionNote) {
        this.id = UUID.randomUUID();
        this.providerId = providerId;
        this.versionNumber = versionNumber;
        this.statusBefore = statusBefore;
        this.statusAfter = statusAfter;
        this.submittedBy = submittedBy;
        this.snapshotHash = snapshotHash;
        this.snapshotJson = snapshotJson;
        this.submissionNote = submissionNote;
        this.submittedAt = OffsetDateTime.now();
    }
    public UUID getId() { return id; }
    public UUID getProviderId() { return providerId; }
    public int getVersionNumber() { return versionNumber; }
    public String getStatusBefore() { return statusBefore; }
    public String getStatusAfter() { return statusAfter; }
    public String getSubmittedBy() { return submittedBy; }
    public String getSnapshotHash() { return snapshotHash; }
    public String getSnapshotJson() { return snapshotJson; }
    public String getSubmissionNote() { return submissionNote; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
}
