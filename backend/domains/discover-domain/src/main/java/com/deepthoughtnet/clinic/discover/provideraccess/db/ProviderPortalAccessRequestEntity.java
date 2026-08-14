package com.deepthoughtnet.clinic.discover.provideraccess.db;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.provideraccess.ProviderPortalAccessRequestStatus;
import com.deepthoughtnet.clinic.discover.provideraccess.ProviderPortalAccessRequestType;
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
        name = "discover_provider_access_requests",
        indexes = {
                @Index(name = "ix_discover_provider_access_requests_status_requested", columnList = "status,requested_at"),
                @Index(name = "ix_discover_provider_access_requests_mobile", columnList = "provider_type,mobile_normalized,requested_at"),
                @Index(name = "ix_discover_provider_access_requests_email", columnList = "provider_type,email_normalized,requested_at"),
                @Index(name = "ix_discover_provider_access_requests_application_ref", columnList = "provider_application_reference,requested_at")
        }
)
public class ProviderPortalAccessRequestEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 32)
    private ProviderPortalAccessRequestType requestType = ProviderPortalAccessRequestType.PROVIDER;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 32)
    private ProviderType providerType;

    @Column(name = "full_name", nullable = false, length = 256)
    private String fullName;

    @Column(length = 256)
    private String email;

    @Column(name = "email_normalized", length = 256)
    private String emailNormalized;

    @Column(nullable = false, length = 64)
    private String mobile;

    @Column(name = "mobile_normalized", nullable = false, length = 32)
    private String mobileNormalized;

    @Column(name = "provider_application_reference", length = 64)
    private String providerApplicationReference;

    @Column(columnDefinition = "text")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProviderPortalAccessRequestStatus status = ProviderPortalAccessRequestStatus.REQUESTED;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_by_display_name", length = 256)
    private String reviewedByDisplayName;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @Column(name = "linked_provider_account_id")
    private UUID linkedProviderAccountId;

    @Column(name = "linked_provider_account_display_name", length = 256)
    private String linkedProviderAccountDisplayName;

    @Column(name = "linked_provider_application_reference", length = 64)
    private String linkedProviderApplicationReference;

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

    protected ProviderPortalAccessRequestEntity() {
    }

    public static ProviderPortalAccessRequestEntity create(
            ProviderType providerType,
            String fullName,
            String email,
            String emailNormalized,
            String mobile,
            String mobileNormalized,
            String providerApplicationReference,
            String note
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        ProviderPortalAccessRequestEntity entity = new ProviderPortalAccessRequestEntity();
        entity.id = UUID.randomUUID();
        entity.providerType = providerType;
        entity.fullName = fullName;
        entity.email = email;
        entity.emailNormalized = emailNormalized;
        entity.mobile = mobile;
        entity.mobileNormalized = mobileNormalized;
        entity.providerApplicationReference = providerApplicationReference;
        entity.note = note;
        entity.status = ProviderPortalAccessRequestStatus.REQUESTED;
        entity.requestedAt = now;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public ProviderPortalAccessRequestType getRequestType() {
        return requestType;
    }

    public ProviderType getProviderType() {
        return providerType;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getEmailNormalized() {
        return emailNormalized;
    }

    public String getMobile() {
        return mobile;
    }

    public String getMobileNormalized() {
        return mobileNormalized;
    }

    public String getProviderApplicationReference() {
        return providerApplicationReference;
    }

    public String getNote() {
        return note;
    }

    public ProviderPortalAccessRequestStatus getStatus() {
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

    public UUID getLinkedProviderAccountId() {
        return linkedProviderAccountId;
    }

    public String getLinkedProviderAccountDisplayName() {
        return linkedProviderAccountDisplayName;
    }

    public String getLinkedProviderApplicationReference() {
        return linkedProviderApplicationReference;
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

    public void approve(
            UUID reviewedBy,
            String reviewedByDisplayName,
            UUID linkedProviderAccountId,
            String linkedProviderAccountDisplayName,
            String linkedProviderApplicationReference,
            String accessCodeHash,
            OffsetDateTime accessCodeIssuedAt,
            OffsetDateTime accessCodeExpiresAt
    ) {
        this.status = ProviderPortalAccessRequestStatus.APPROVED;
        this.reviewedBy = reviewedBy;
        this.reviewedByDisplayName = reviewedByDisplayName;
        this.reviewedAt = OffsetDateTime.now();
        this.approvedAt = this.reviewedAt;
        this.linkedProviderAccountId = linkedProviderAccountId;
        this.linkedProviderAccountDisplayName = linkedProviderAccountDisplayName;
        this.linkedProviderApplicationReference = linkedProviderApplicationReference;
        this.accessCodeHash = accessCodeHash;
        this.accessCodeIssuedAt = accessCodeIssuedAt;
        this.accessCodeExpiresAt = accessCodeExpiresAt;
        this.updatedAt = this.reviewedAt;
    }

    public void reject(UUID reviewedBy, String reviewedByDisplayName, String reason) {
        this.status = ProviderPortalAccessRequestStatus.REJECTED;
        this.reviewedBy = reviewedBy;
        this.reviewedByDisplayName = reviewedByDisplayName;
        this.reviewedAt = OffsetDateTime.now();
        this.rejectionReason = reason;
        this.updatedAt = this.reviewedAt;
    }

    public void revoke(UUID reviewedBy, String reviewedByDisplayName, String reason) {
        this.status = ProviderPortalAccessRequestStatus.REVOKED;
        this.reviewedBy = reviewedBy;
        this.reviewedByDisplayName = reviewedByDisplayName;
        this.reviewedAt = OffsetDateTime.now();
        this.revokedAt = this.reviewedAt;
        this.rejectionReason = reason;
        this.updatedAt = this.reviewedAt;
    }

    public void setLinkedProviderAccountId(UUID linkedProviderAccountId) {
        this.linkedProviderAccountId = linkedProviderAccountId;
        this.updatedAt = OffsetDateTime.now();
    }

    public void setLinkedProviderAccountDisplayName(String linkedProviderAccountDisplayName) {
        this.linkedProviderAccountDisplayName = linkedProviderAccountDisplayName;
        this.updatedAt = OffsetDateTime.now();
    }

    public void attachAccessCode(String accessCodeHash, OffsetDateTime issuedAt, OffsetDateTime expiresAt) {
        this.accessCodeHash = accessCodeHash;
        this.accessCodeIssuedAt = issuedAt;
        this.accessCodeExpiresAt = expiresAt;
        this.updatedAt = OffsetDateTime.now();
    }
}
