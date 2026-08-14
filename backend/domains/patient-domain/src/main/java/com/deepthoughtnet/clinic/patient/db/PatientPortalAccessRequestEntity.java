package com.deepthoughtnet.clinic.patient.db;

import com.deepthoughtnet.clinic.patient.service.model.PatientPortalAccessRequestStatus;
import com.deepthoughtnet.clinic.patient.service.model.PatientPortalAccessRequestType;
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
@Table(
        name = "patient_portal_access_requests",
        indexes = {
                @Index(name = "ix_patient_portal_access_requests_tenant_requested", columnList = "tenant_id,requested_at"),
                @Index(name = "ix_patient_portal_access_requests_tenant_mobile", columnList = "tenant_id,mobile_normalized,requested_at"),
                @Index(name = "ix_patient_portal_access_requests_tenant_status", columnList = "tenant_id,status,requested_at")
        }
)
public class PatientPortalAccessRequestEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 32)
    private PatientPortalAccessRequestType requestType = PatientPortalAccessRequestType.PATIENT;

    @Column(name = "full_name", nullable = false, length = 256)
    private String fullName;

    @Column(name = "mobile", nullable = false, length = 64)
    private String mobile;

    @Column(name = "mobile_normalized", nullable = false, length = 32)
    private String mobileNormalized;

    @Column(length = 256)
    private String email;

    @Column(columnDefinition = "text")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PatientPortalAccessRequestStatus status = PatientPortalAccessRequestStatus.REQUESTED;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_by_display_name", length = 256)
    private String reviewedByDisplayName;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @Column(name = "linked_patient_id")
    private UUID linkedPatientId;

    @Column(name = "linked_patient_display_name", length = 256)
    private String linkedPatientDisplayName;

    @Column(name = "access_code_hash", length = 256)
    private String accessCodeHash;

    @Column(name = "access_code_issued_at")
    private OffsetDateTime accessCodeIssuedAt;

    @Column(name = "access_code_expires_at")
    private OffsetDateTime accessCodeExpiresAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected PatientPortalAccessRequestEntity() {
    }

    public static PatientPortalAccessRequestEntity create(UUID tenantId, String fullName, String mobile, String mobileNormalized, String email, String note) {
        PatientPortalAccessRequestEntity entity = new PatientPortalAccessRequestEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.fullName = fullName;
        entity.mobile = mobile;
        entity.mobileNormalized = mobileNormalized;
        entity.email = email;
        entity.note = note;
        entity.status = PatientPortalAccessRequestStatus.REQUESTED;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        entity.requestedAt = entity.createdAt;
        entity.reviewedAt = null;
        entity.approvedAt = null;
        entity.activatedAt = null;
        entity.revokedAt = null;
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public PatientPortalAccessRequestType getRequestType() {
        return requestType;
    }

    public String getFullName() {
        return fullName;
    }

    public String getMobile() {
        return mobile;
    }

    public String getMobileNormalized() {
        return mobileNormalized;
    }

    public String getEmail() {
        return email;
    }

    public String getNote() {
        return note;
    }

    public PatientPortalAccessRequestStatus getStatus() {
        return status;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }

    public OffsetDateTime getRequestedAt() {
        return requestedAt;
    }

    public OffsetDateTime getApprovedAt() {
        return approvedAt;
    }

    public OffsetDateTime getActivatedAt() {
        return activatedAt;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public String getReviewedByDisplayName() {
        return reviewedByDisplayName;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public UUID getLinkedPatientId() {
        return linkedPatientId;
    }

    public String getLinkedPatientDisplayName() {
        return linkedPatientDisplayName;
    }

    public String getAccessCodeHash() {
        return accessCodeHash;
    }

    public OffsetDateTime getAccessCodeIssuedAt() {
        return accessCodeIssuedAt;
    }

    public OffsetDateTime getAccessCodeExpiresAt() {
        return accessCodeExpiresAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public void attachAccessCode(String accessCodeHash, OffsetDateTime issuedAt, OffsetDateTime expiresAt) {
        this.accessCodeHash = accessCodeHash;
        this.accessCodeIssuedAt = issuedAt;
        this.accessCodeExpiresAt = expiresAt;
        this.updatedAt = OffsetDateTime.now();
    }

    public void approve(UUID reviewedBy, String reviewedByDisplayName, UUID linkedPatientId, String linkedPatientDisplayName) {
        this.status = PatientPortalAccessRequestStatus.APPROVED;
        this.reviewedBy = reviewedBy;
        this.reviewedByDisplayName = reviewedByDisplayName;
        this.reviewedAt = OffsetDateTime.now();
        this.approvedAt = this.reviewedAt;
        this.linkedPatientId = linkedPatientId;
        this.linkedPatientDisplayName = linkedPatientDisplayName;
        this.rejectionReason = null;
        this.revokedAt = null;
        this.updatedAt = this.reviewedAt;
    }

    public void linkPatient(UUID linkedPatientId, String linkedPatientDisplayName) {
        this.linkedPatientId = linkedPatientId;
        this.linkedPatientDisplayName = linkedPatientDisplayName;
        this.updatedAt = OffsetDateTime.now();
    }

    public void activate() {
        this.status = PatientPortalAccessRequestStatus.ACTIVE;
        this.activatedAt = OffsetDateTime.now();
        this.updatedAt = this.activatedAt;
    }

    public void reject(UUID reviewedBy, String reviewedByDisplayName, String rejectionReason) {
        this.status = PatientPortalAccessRequestStatus.REJECTED;
        this.reviewedBy = reviewedBy;
        this.reviewedByDisplayName = reviewedByDisplayName;
        this.reviewedAt = OffsetDateTime.now();
        this.rejectionReason = rejectionReason;
        this.updatedAt = this.reviewedAt;
    }

    public void revoke(UUID reviewedBy, String reviewedByDisplayName, String reason) {
        this.status = PatientPortalAccessRequestStatus.REVOKED;
        this.reviewedBy = reviewedBy;
        this.reviewedByDisplayName = reviewedByDisplayName;
        this.reviewedAt = OffsetDateTime.now();
        this.revokedAt = this.reviewedAt;
        this.rejectionReason = reason;
        this.updatedAt = this.reviewedAt;
    }
}
