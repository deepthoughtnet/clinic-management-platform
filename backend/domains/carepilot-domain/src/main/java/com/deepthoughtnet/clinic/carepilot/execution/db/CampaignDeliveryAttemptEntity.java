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
 * Immutable attempt-level delivery audit log for CarePilot executions.
 */
@Entity
@Table(name = "carepilot_delivery_attempts", indexes = {
        @Index(name = "ix_cp_attempt_exec_attempt", columnList = "execution_id,attempt_number"),
        @Index(name = "ix_cp_attempt_tenant_attempted", columnList = "tenant_id,attempted_at")
})
public class CampaignDeliveryAttemptEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "execution_id", nullable = false)
    private UUID executionId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "provider_name", length = 80)
    private String providerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false, length = 24)
    private ChannelType channelType;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 40)
    private MessageDeliveryStatus deliveryStatus;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "attempted_at", nullable = false)
    private OffsetDateTime attemptedAt;

    protected CampaignDeliveryAttemptEntity() {
    }

    public static CampaignDeliveryAttemptEntity create(UUID tenantId,
                                                       UUID executionId,
                                                       int attemptNumber,
                                                       String providerName,
                                                       ChannelType channelType,
                                                       MessageDeliveryStatus deliveryStatus,
                                                       String errorCode,
                                                       String errorMessage,
                                                       OffsetDateTime attemptedAt) {
        CampaignDeliveryAttemptEntity entity = new CampaignDeliveryAttemptEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.executionId = executionId;
        entity.attemptNumber = attemptNumber;
        entity.providerName = providerName;
        entity.channelType = channelType;
        entity.deliveryStatus = deliveryStatus;
        entity.errorCode = errorCode;
        entity.errorMessage = errorMessage;
        entity.attemptedAt = attemptedAt == null ? OffsetDateTime.now() : attemptedAt;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getExecutionId() { return executionId; }
    public int getAttemptNumber() { return attemptNumber; }
    public String getProviderName() { return providerName; }
    public ChannelType getChannelType() { return channelType; }
    public MessageDeliveryStatus getDeliveryStatus() { return deliveryStatus; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public OffsetDateTime getAttemptedAt() { return attemptedAt; }
}
