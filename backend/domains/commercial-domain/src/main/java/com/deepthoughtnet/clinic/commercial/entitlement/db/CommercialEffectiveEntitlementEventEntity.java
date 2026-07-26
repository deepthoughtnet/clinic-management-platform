package com.deepthoughtnet.clinic.commercial.entitlement.db;

import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementEnums.GenerationReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "commercial_effective_entitlement_events", indexes = {
        @Index(name = "ix_commercial_effective_entitlement_events_tenant", columnList = "tenant_id,occurred_at"),
        @Index(name = "ix_commercial_effective_entitlement_events_snapshot", columnList = "snapshot_id"),
        @Index(name = "ix_commercial_effective_entitlement_events_override", columnList = "override_id")
})
public class CommercialEffectiveEntitlementEventEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "snapshot_id")
    private UUID snapshotId;

    @Column(name = "override_id")
    private UUID overrideId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "generation_reason", length = 64)
    private GenerationReason generationReason;

    @Column(name = "validation_state", length = 32)
    private String validationState;

    @Column(name = "payload_json", columnDefinition = "jsonb")
    private String payloadJson;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "actor")
    private String actor;

    @Version
    @Column(nullable = false)
    private long version;

    protected CommercialEffectiveEntitlementEventEntity() {
    }

    public static CommercialEffectiveEntitlementEventEntity create(
            UUID id,
            UUID tenantId,
            UUID subscriptionId,
            UUID snapshotId,
            UUID overrideId,
            String eventType,
            GenerationReason generationReason,
            String validationState,
            String payloadJson,
            OffsetDateTime occurredAt,
            String actor
    ) {
        CommercialEffectiveEntitlementEventEntity entity = new CommercialEffectiveEntitlementEventEntity();
        entity.id = id;
        entity.tenantId = tenantId;
        entity.subscriptionId = subscriptionId;
        entity.snapshotId = snapshotId;
        entity.overrideId = overrideId;
        entity.eventType = eventType;
        entity.generationReason = generationReason;
        entity.validationState = validationState;
        entity.payloadJson = payloadJson;
        entity.occurredAt = occurredAt;
        entity.actor = actor;
        entity.version = 0L;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getSubscriptionId() { return subscriptionId; }
    public UUID getSnapshotId() { return snapshotId; }
    public UUID getOverrideId() { return overrideId; }
    public String getEventType() { return eventType; }
    public GenerationReason getGenerationReason() { return generationReason; }
    public String getValidationState() { return validationState; }
    public String getPayloadJson() { return payloadJson; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public String getActor() { return actor; }
    public long getVersion() { return version; }
}
