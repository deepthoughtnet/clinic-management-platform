package com.deepthoughtnet.clinic.discover.verification.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "discover_provider_accounts")
public class DiscoverProviderAccountEntity {
    @Id
    private UUID id;

    @Column(name = "normalized_email", unique = true, length = 256)
    private String normalizedEmail;

    @Column(name = "normalized_phone", unique = true, length = 32)
    private String normalizedPhone;

    @Column(name = "email_verified_at")
    private OffsetDateTime emailVerifiedAt;

    @Column(name = "phone_verified_at")
    private OffsetDateTime phoneVerifiedAt;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected DiscoverProviderAccountEntity() {
    }

    public static DiscoverProviderAccountEntity create(String normalizedEmail, String normalizedPhone) {
        OffsetDateTime now = OffsetDateTime.now();
        DiscoverProviderAccountEntity entity = new DiscoverProviderAccountEntity();
        entity.id = UUID.randomUUID();
        entity.normalizedEmail = normalizedEmail;
        entity.normalizedPhone = normalizedPhone;
        entity.status = "ACTIVE";
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public String getNormalizedEmail() {
        return normalizedEmail;
    }

    public void setNormalizedEmail(String normalizedEmail) {
        this.normalizedEmail = normalizedEmail;
        this.updatedAt = OffsetDateTime.now();
    }

    public String getNormalizedPhone() {
        return normalizedPhone;
    }

    public void setNormalizedPhone(String normalizedPhone) {
        this.normalizedPhone = normalizedPhone;
        this.updatedAt = OffsetDateTime.now();
    }

    public OffsetDateTime getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public void markEmailVerified() {
        this.emailVerifiedAt = OffsetDateTime.now();
        this.updatedAt = this.emailVerifiedAt;
    }

    public OffsetDateTime getPhoneVerifiedAt() {
        return phoneVerifiedAt;
    }

    public void markPhoneVerified() {
        this.phoneVerifiedAt = OffsetDateTime.now();
        this.updatedAt = this.phoneVerifiedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = OffsetDateTime.now();
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
