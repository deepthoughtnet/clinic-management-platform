package com.deepthoughtnet.clinic.carepilot.execution.db;

import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable provider webhook event ledger for post-send delivery lifecycle tracking.
 */
@Entity
@Table(name = "carepilot_delivery_events", indexes = {
        @Index(name = "ix_cp_delivery_events_tenant_received", columnList = "tenant_id,received_at"),
        @Index(name = "ix_cp_delivery_events_execution", columnList = "execution_id,event_timestamp"),
        @Index(name = "ix_cp_delivery_events_provider_msg", columnList = "provider_name,provider_message_id")
})
public class CampaignDeliveryEventEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "execution_id")
    private UUID executionId;

    @Column(name = "delivery_attempt_id")
    private UUID deliveryAttemptId;

    @Column(name = "provider_name", length = 80, nullable = false)
    private String providerName;

    @Column(name = "provider_message_id", length = 180)
    private String providerMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false, length = 24)
    private ChannelType channelType;

    @Column(name = "external_status", length = 80)
    private String externalStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "internal_status", nullable = false, length = 40)
    private MessageDeliveryStatus internalStatus;

    @Column(name = "event_type", length = 80, nullable = false)
    private String eventType;

    @Column(name = "event_timestamp")
    private OffsetDateTime eventTimestamp;

    @Column(name = "raw_payload_redacted", columnDefinition = "text")
    private String rawPayloadRedacted;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected CampaignDeliveryEventEntity() {
    }

    /**
     * Creates an immutable webhook delivery event record.
     */
    public static CampaignDeliveryEventEntity create(
            UUID tenantId,
            UUID executionId,
            UUID deliveryAttemptId,
            String providerName,
            String providerMessageId,
            ChannelType channelType,
            String externalStatus,
            MessageDeliveryStatus internalStatus,
            String eventType,
            OffsetDateTime eventTimestamp,
            String rawPayloadRedacted
    ) {
        CampaignDeliveryEventEntity entity = new CampaignDeliveryEventEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.executionId = executionId;
        entity.deliveryAttemptId = deliveryAttemptId;
        entity.providerName = providerName;
        entity.providerMessageId = providerMessageId;
        entity.channelType = channelType;
        entity.externalStatus = externalStatus;
        entity.internalStatus = internalStatus;
        entity.eventType = eventType;
        entity.eventTimestamp = eventTimestamp;
        entity.rawPayloadRedacted = rawPayloadRedacted;
        entity.receivedAt = OffsetDateTime.now();
        entity.createdAt = entity.receivedAt;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getExecutionId() { return executionId; }
    public UUID getDeliveryAttemptId() { return deliveryAttemptId; }
    public String getProviderName() { return providerName; }
    public String getProviderMessageId() { return providerMessageId; }
    public ChannelType getChannelType() { return channelType; }
    public String getExternalStatus() { return externalStatus; }
    public MessageDeliveryStatus getInternalStatus() { return internalStatus; }
    public String getEventType() { return eventType; }
    public OffsetDateTime getEventTimestamp() { return eventTimestamp; }
    public String getRawPayloadRedacted() { return rawPayloadRedacted; }
    public OffsetDateTime getReceivedAt() { return receivedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
