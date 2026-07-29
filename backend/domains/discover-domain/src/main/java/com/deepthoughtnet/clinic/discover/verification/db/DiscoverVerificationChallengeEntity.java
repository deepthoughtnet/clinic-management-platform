package com.deepthoughtnet.clinic.discover.verification.db;

import com.deepthoughtnet.clinic.discover.verification.VerificationChannel;
import com.deepthoughtnet.clinic.discover.verification.VerificationPurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "discover_verification_challenges")
public class DiscoverVerificationChallengeEntity {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 64)
    private VerificationPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16)
    private VerificationChannel channel;

    @Column(name = "normalized_recipient", nullable = false, length = 256)
    private String normalizedRecipient;

    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    @Column(name = "provider_application_id")
    private UUID providerApplicationId;

    @Column(name = "provider_account_id")
    private UUID providerAccountId;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "resend_available_at", nullable = false)
    private OffsetDateTime resendAvailableAt;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    @Column(name = "invalidated_at")
    private OffsetDateTime invalidatedAt;

    @Column(name = "delivery_provider", nullable = false, length = 128)
    private String deliveryProvider;

    @Column(name = "delivery_reference", length = 256)
    private String deliveryReference;

    @Column(name = "created_by_context", nullable = false, length = 128)
    private String createdByContext;

    @Column(name = "code_hint", length = 16)
    private String codeHint;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected DiscoverVerificationChallengeEntity() {
    }

    public static DiscoverVerificationChallengeEntity create(
            UUID providerApplicationId,
            UUID providerAccountId,
            VerificationPurpose purpose,
            VerificationChannel channel,
            String normalizedRecipient,
            String codeHash,
            int maxAttempts,
            OffsetDateTime expiresAt,
            OffsetDateTime resendAvailableAt,
            String deliveryProvider,
            String deliveryReference,
            String createdByContext,
            String codeHint
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        DiscoverVerificationChallengeEntity entity = new DiscoverVerificationChallengeEntity();
        entity.id = UUID.randomUUID();
        entity.providerApplicationId = providerApplicationId;
        entity.providerAccountId = providerAccountId;
        entity.purpose = purpose;
        entity.channel = channel;
        entity.normalizedRecipient = normalizedRecipient;
        entity.codeHash = codeHash;
        entity.maxAttempts = maxAttempts;
        entity.expiresAt = expiresAt;
        entity.resendAvailableAt = resendAvailableAt;
        entity.deliveryProvider = deliveryProvider;
        entity.deliveryReference = deliveryReference;
        entity.createdByContext = createdByContext;
        entity.codeHint = codeHint;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public VerificationPurpose getPurpose() {
        return purpose;
    }

    public VerificationChannel getChannel() {
        return channel;
    }

    public String getNormalizedRecipient() {
        return normalizedRecipient;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public UUID getProviderApplicationId() {
        return providerApplicationId;
    }

    public UUID getProviderAccountId() {
        return providerAccountId;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public OffsetDateTime getResendAvailableAt() {
        return resendAvailableAt;
    }

    public OffsetDateTime getConsumedAt() {
        return consumedAt;
    }

    public OffsetDateTime getInvalidatedAt() {
        return invalidatedAt;
    }

    public String getDeliveryProvider() {
        return deliveryProvider;
    }

    public void setDeliveryProvider(String deliveryProvider) {
        this.deliveryProvider = deliveryProvider;
        this.updatedAt = OffsetDateTime.now();
    }

    public String getDeliveryReference() {
        return deliveryReference;
    }

    public void setDeliveryReference(String deliveryReference) {
        this.deliveryReference = deliveryReference;
        this.updatedAt = OffsetDateTime.now();
    }

    public String getCreatedByContext() {
        return createdByContext;
    }

    public String getCodeHint() {
        return codeHint;
    }

    public void setCodeHint(String codeHint) {
        this.codeHint = codeHint;
        this.updatedAt = OffsetDateTime.now();
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void incrementAttemptCount() {
        this.attemptCount += 1;
        this.updatedAt = OffsetDateTime.now();
    }

    public void markConsumed() {
        this.consumedAt = OffsetDateTime.now();
        this.updatedAt = this.consumedAt;
    }

    public void invalidate() {
        this.invalidatedAt = OffsetDateTime.now();
        this.updatedAt = this.invalidatedAt;
    }
}
