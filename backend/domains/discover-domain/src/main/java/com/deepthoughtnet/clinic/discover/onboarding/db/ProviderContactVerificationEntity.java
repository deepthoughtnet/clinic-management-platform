package com.deepthoughtnet.clinic.discover.onboarding.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "discover_provider_contact_verifications")
public class ProviderContactVerificationEntity {
    @Id
    @Column(name = "provider_id", nullable = false)
    private UUID providerId;
    @Column(name = "email_normalized", nullable = false, length = 256)
    private String emailNormalized;
    @Column(name = "phone_normalized", nullable = false, length = 32)
    private String phoneNormalized;
    @Column(name = "email_otp_hash", length = 255)
    private String emailOtpHash;
    @Column(name = "email_otp_expires_at")
    private OffsetDateTime emailOtpExpiresAt;
    @Column(name = "email_otp_attempts", nullable = false)
    private int emailOtpAttempts;
    @Column(name = "email_otp_sent_at")
    private OffsetDateTime emailOtpSentAt;
    @Column(name = "email_verified_at")
    private OffsetDateTime emailVerifiedAt;
    @Column(name = "phone_otp_hash", length = 255)
    private String phoneOtpHash;
    @Column(name = "phone_otp_expires_at")
    private OffsetDateTime phoneOtpExpiresAt;
    @Column(name = "phone_otp_attempts", nullable = false)
    private int phoneOtpAttempts;
    @Column(name = "phone_otp_sent_at")
    private OffsetDateTime phoneOtpSentAt;
    @Column(name = "phone_verified_at")
    private OffsetDateTime phoneVerifiedAt;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ProviderContactVerificationEntity() {
    }

    public static ProviderContactVerificationEntity create(UUID providerId, String emailNormalized, String phoneNormalized) {
        OffsetDateTime now = OffsetDateTime.now();
        ProviderContactVerificationEntity entity = new ProviderContactVerificationEntity();
        entity.providerId = providerId;
        entity.emailNormalized = emailNormalized;
        entity.phoneNormalized = phoneNormalized;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public UUID getProviderId() {
        return providerId;
    }

    public String getEmailNormalized() {
        return emailNormalized;
    }

    public void setEmailNormalized(String emailNormalized) {
        this.emailNormalized = emailNormalized;
        this.updatedAt = OffsetDateTime.now();
    }

    public String getPhoneNormalized() {
        return phoneNormalized;
    }

    public void setPhoneNormalized(String phoneNormalized) {
        this.phoneNormalized = phoneNormalized;
        this.updatedAt = OffsetDateTime.now();
    }

    public String getEmailOtpHash() {
        return emailOtpHash;
    }

    public void setEmailOtpHash(String emailOtpHash) {
        this.emailOtpHash = emailOtpHash;
        this.updatedAt = OffsetDateTime.now();
    }

    public OffsetDateTime getEmailOtpExpiresAt() {
        return emailOtpExpiresAt;
    }

    public void setEmailOtpExpiresAt(OffsetDateTime emailOtpExpiresAt) {
        this.emailOtpExpiresAt = emailOtpExpiresAt;
        this.updatedAt = OffsetDateTime.now();
    }

    public int getEmailOtpAttempts() {
        return emailOtpAttempts;
    }

    public void resetEmailOtpAttempts() {
        this.emailOtpAttempts = 0;
        this.updatedAt = OffsetDateTime.now();
    }

    public void incrementEmailOtpAttempts() {
        this.emailOtpAttempts += 1;
        this.updatedAt = OffsetDateTime.now();
    }

    public OffsetDateTime getEmailOtpSentAt() {
        return emailOtpSentAt;
    }

    public void setEmailOtpSentAt(OffsetDateTime emailOtpSentAt) {
        this.emailOtpSentAt = emailOtpSentAt;
        this.updatedAt = OffsetDateTime.now();
    }

    public OffsetDateTime getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public void markEmailVerified() {
        this.emailVerifiedAt = OffsetDateTime.now();
        this.updatedAt = this.emailVerifiedAt;
    }

    public String getPhoneOtpHash() {
        return phoneOtpHash;
    }

    public void setPhoneOtpHash(String phoneOtpHash) {
        this.phoneOtpHash = phoneOtpHash;
        this.updatedAt = OffsetDateTime.now();
    }

    public OffsetDateTime getPhoneOtpExpiresAt() {
        return phoneOtpExpiresAt;
    }

    public void setPhoneOtpExpiresAt(OffsetDateTime phoneOtpExpiresAt) {
        this.phoneOtpExpiresAt = phoneOtpExpiresAt;
        this.updatedAt = OffsetDateTime.now();
    }

    public int getPhoneOtpAttempts() {
        return phoneOtpAttempts;
    }

    public void resetPhoneOtpAttempts() {
        this.phoneOtpAttempts = 0;
        this.updatedAt = OffsetDateTime.now();
    }

    public void incrementPhoneOtpAttempts() {
        this.phoneOtpAttempts += 1;
        this.updatedAt = OffsetDateTime.now();
    }

    public OffsetDateTime getPhoneOtpSentAt() {
        return phoneOtpSentAt;
    }

    public void setPhoneOtpSentAt(OffsetDateTime phoneOtpSentAt) {
        this.phoneOtpSentAt = phoneOtpSentAt;
        this.updatedAt = OffsetDateTime.now();
    }

    public OffsetDateTime getPhoneVerifiedAt() {
        return phoneVerifiedAt;
    }

    public void markPhoneVerified() {
        this.phoneVerifiedAt = OffsetDateTime.now();
        this.updatedAt = this.phoneVerifiedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
