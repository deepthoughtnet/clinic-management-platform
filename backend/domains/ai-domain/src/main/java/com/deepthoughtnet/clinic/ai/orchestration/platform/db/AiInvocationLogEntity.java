package com.deepthoughtnet.clinic.ai.orchestration.platform.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Metadata-first AI invocation logs for usage, operational analytics, and audits.
 */
@Entity
@Table(name = "ai_invocation_logs", indexes = {
        @Index(name = "ix_ai_invocation_logs_tenant_created", columnList = "tenant_id,created_at"),
        @Index(name = "ix_ai_invocation_logs_tenant_status", columnList = "tenant_id,status"),
        @Index(name = "ix_ai_invocation_logs_tenant_provider", columnList = "tenant_id,provider_name"),
        @Index(name = "ix_ai_invocation_logs_tenant_use_case", columnList = "tenant_id,use_case")
})
public class AiInvocationLogEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column
    private String domain;

    @Column(name = "use_case")
    private String useCase;

    @Column(name = "prompt_key")
    private String promptKey;

    @Column(name = "prompt_version")
    private Integer promptVersion;

    @Column(name = "provider_name")
    private String providerName;

    @Column(name = "model_name")
    private String modelName;

    @Column(nullable = false)
    private String status;

    @Column(name = "input_token_count")
    private Long inputTokenCount;

    @Column(name = "output_token_count")
    private Long outputTokenCount;

    @Column(name = "estimated_cost", precision = 19, scale = 6)
    private BigDecimal estimatedCost;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "request_payload_redacted", columnDefinition = "text")
    private String requestPayloadRedacted;

    @Column(name = "response_payload_redacted", columnDefinition = "text")
    private String responsePayloadRedacted;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    protected AiInvocationLogEntity() {}

    public static AiInvocationLogEntity create(UUID tenantId, UUID requestId, String correlationId, String domain,
                                               String useCase, String promptKey, Integer promptVersion,
                                               String providerName, String modelName, String status,
                                               Long inputTokens, Long outputTokens, BigDecimal estimatedCost,
                                               Long latencyMs, String requestPayloadRedacted,
                                               String responsePayloadRedacted, String errorCode,
                                               String errorMessage, UUID createdBy) {
        AiInvocationLogEntity entity = new AiInvocationLogEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.requestId = requestId;
        entity.correlationId = correlationId;
        entity.domain = domain;
        entity.useCase = useCase;
        entity.promptKey = promptKey;
        entity.promptVersion = promptVersion;
        entity.providerName = providerName;
        entity.modelName = modelName;
        entity.status = status;
        entity.inputTokenCount = inputTokens;
        entity.outputTokenCount = outputTokens;
        entity.estimatedCost = estimatedCost;
        entity.latencyMs = latencyMs;
        entity.requestPayloadRedacted = requestPayloadRedacted;
        entity.responsePayloadRedacted = responsePayloadRedacted;
        entity.errorCode = errorCode;
        entity.errorMessage = errorMessage;
        entity.createdAt = OffsetDateTime.now();
        entity.createdBy = createdBy;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getRequestId() { return requestId; }
    public String getCorrelationId() { return correlationId; }
    public String getDomain() { return domain; }
    public String getUseCase() { return useCase; }
    public String getPromptKey() { return promptKey; }
    public Integer getPromptVersion() { return promptVersion; }
    public String getProviderName() { return providerName; }
    public String getModelName() { return modelName; }
    public String getStatus() { return status; }
    public Long getInputTokenCount() { return inputTokenCount; }
    public Long getOutputTokenCount() { return outputTokenCount; }
    public BigDecimal getEstimatedCost() { return estimatedCost; }
    public Long getLatencyMs() { return latencyMs; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
