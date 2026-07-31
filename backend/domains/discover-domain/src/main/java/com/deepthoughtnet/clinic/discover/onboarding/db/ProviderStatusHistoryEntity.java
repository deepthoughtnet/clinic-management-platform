package com.deepthoughtnet.clinic.discover.onboarding.db;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderLifecycleStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "discover_provider_status_history")
public class ProviderStatusHistoryEntity {
    @Id
    private UUID id;
    @Column(name = "provider_id", nullable = false)
    private UUID providerId;
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 32)
    private ProviderLifecycleStatus fromStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private ProviderLifecycleStatus toStatus;
    @Column(name = "actor_category", length = 64)
    private String actorCategory;
    @Column(length = 512)
    private String reason;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected ProviderStatusHistoryEntity() {
    }

    public ProviderStatusHistoryEntity(UUID providerId, ProviderLifecycleStatus fromStatus, ProviderLifecycleStatus toStatus, String reason) {
        this(providerId, fromStatus, toStatus, null, reason);
    }

    public ProviderStatusHistoryEntity(UUID providerId, ProviderLifecycleStatus fromStatus, ProviderLifecycleStatus toStatus, String actorCategory, String reason) {
        this.id = UUID.randomUUID();
        this.providerId = providerId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorCategory = actorCategory;
        this.reason = reason;
        this.createdAt = OffsetDateTime.now();
    }
    public UUID getId() { return id; }
    public UUID getProviderId() { return providerId; }
    public ProviderLifecycleStatus getFromStatus() { return fromStatus; }
    public ProviderLifecycleStatus getToStatus() { return toStatus; }
    public String getActorCategory() { return actorCategory; }
    public String getReason() { return reason; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
