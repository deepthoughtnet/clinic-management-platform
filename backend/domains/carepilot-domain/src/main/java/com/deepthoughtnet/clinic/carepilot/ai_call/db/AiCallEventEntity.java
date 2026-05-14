package com.deepthoughtnet.clinic.carepilot.ai_call.db;

import com.deepthoughtnet.clinic.carepilot.ai_call.model.AiCallEventType;
import com.deepthoughtnet.clinic.carepilot.ai_call.model.AiCallExecutionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Append-only persistence entity for AI call execution events. */
@Entity
@Table(name = "carepilot_ai_call_events", indexes = {
        @Index(name = "ix_cp_ai_call_events_tenant_execution_created", columnList = "tenant_id,execution_id,created_at"),
        @Index(name = "ix_cp_ai_call_events_tenant_provider_call", columnList = "tenant_id,provider_call_id"),
        @Index(name = "ix_cp_ai_call_events_tenant_created", columnList = "tenant_id,created_at")
})
public class AiCallEventEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "execution_id")
    private UUID executionId;

    @Column(name = "provider_name", nullable = false, length = 64)
    private String providerName;

    @Column(name = "provider_call_id", length = 128)
    private String providerCallId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 48)
    private AiCallEventType eventType;

    @Column(name = "external_status", length = 64)
    private String externalStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "internal_status", length = 32)
    private AiCallExecutionStatus internalStatus;

    @Column(name = "event_timestamp", nullable = false)
    private OffsetDateTime eventTimestamp;

    @Column(name = "raw_payload_redacted", columnDefinition = "text")
    private String rawPayloadRedacted;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected AiCallEventEntity() {}

    public static AiCallEventEntity create(
            UUID tenantId,
            UUID executionId,
            String providerName,
            String providerCallId,
            AiCallEventType eventType,
            String externalStatus,
            AiCallExecutionStatus internalStatus,
            OffsetDateTime eventTimestamp,
            String rawPayloadRedacted
    ) {
        AiCallEventEntity row = new AiCallEventEntity();
        row.id = UUID.randomUUID();
        row.tenantId = tenantId;
        row.executionId = executionId;
        row.providerName = providerName == null ? "unknown" : providerName;
        row.providerCallId = providerCallId;
        row.eventType = eventType;
        row.externalStatus = externalStatus;
        row.internalStatus = internalStatus;
        row.eventTimestamp = eventTimestamp == null ? OffsetDateTime.now() : eventTimestamp;
        row.rawPayloadRedacted = rawPayloadRedacted;
        row.createdAt = OffsetDateTime.now();
        return row;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getExecutionId() { return executionId; }
    public String getProviderName() { return providerName; }
    public String getProviderCallId() { return providerCallId; }
    public AiCallEventType getEventType() { return eventType; }
    public String getExternalStatus() { return externalStatus; }
    public AiCallExecutionStatus getInternalStatus() { return internalStatus; }
    public OffsetDateTime getEventTimestamp() { return eventTimestamp; }
    public String getRawPayloadRedacted() { return rawPayloadRedacted; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
