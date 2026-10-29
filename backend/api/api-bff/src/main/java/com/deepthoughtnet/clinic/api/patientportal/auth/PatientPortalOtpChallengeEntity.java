package com.deepthoughtnet.clinic.api.patientportal.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "patient_portal_otp_challenges",
        indexes = {
                @Index(name = "ix_patient_portal_otp_tenant_phone_created", columnList = "tenant_id,phone_normalized,created_at"),
                @Index(name = "ix_patient_portal_otp_phone_created", columnList = "phone_normalized,created_at")
        }
)
public class PatientPortalOtpChallengeEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "phone_normalized", nullable = false, length = 32)
    private String phoneNormalized;

    @Column(name = "identified_patient_id")
    private UUID identifiedPatientId;

    @Column(name = "provider_call_id", length = 200)
    private String providerCallId;

    @Column(name = "interaction_id")
    private UUID interactionId;

    @Column(name = "channel", length = 24)
    private String channel;

    @Column(name = "otp_hash", nullable = false, length = 255)
    private String otpHash;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected PatientPortalOtpChallengeEntity() {
    }

    public static PatientPortalOtpChallengeEntity create(
            UUID tenantId,
            String phoneNormalized,
            String otpHash,
            OffsetDateTime expiresAt
    ) {
        PatientPortalOtpChallengeEntity entity = new PatientPortalOtpChallengeEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.phoneNormalized = phoneNormalized;
        entity.otpHash = otpHash;
        entity.attempts = 0;
        entity.expiresAt = expiresAt;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public static PatientPortalOtpChallengeEntity createVoice(UUID tenantId, UUID patientId, String phoneNormalized,
            String otpHash, OffsetDateTime expiresAt, String providerCallId, UUID interactionId) {
        PatientPortalOtpChallengeEntity entity = create(tenantId, phoneNormalized, otpHash, expiresAt);
        entity.identifiedPatientId = patientId;
        entity.providerCallId = providerCallId;
        entity.interactionId = interactionId;
        entity.channel = "VOICE";
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getPhoneNormalized() {
        return phoneNormalized;
    }

    public UUID getIdentifiedPatientId() { return identifiedPatientId; }
    public String getProviderCallId() { return providerCallId; }
    public UUID getInteractionId() { return interactionId; }
    public String getChannel() { return channel; }

    public String getOtpHash() {
        return otpHash;
    }

    public int getAttempts() {
        return attempts;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }

    public void incrementAttempts() {
        this.attempts += 1;
        this.updatedAt = OffsetDateTime.now();
    }

    public void markVerified() {
        this.verifiedAt = OffsetDateTime.now();
        this.updatedAt = this.verifiedAt;
    }
}
