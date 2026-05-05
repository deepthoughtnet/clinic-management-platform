package com.deepthoughtnet.clinic.ai.orchestration.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "ai_request_audit",
        indexes = {
                @Index(name = "ix_ai_request_audit_product", columnList = "product_code"),
                @Index(name = "ix_ai_request_audit_tenant", columnList = "tenant_id"),
                @Index(name = "ix_ai_request_audit_product_tenant", columnList = "product_code,tenant_id"),
                @Index(name = "ix_ai_request_audit_tenant_task", columnList = "tenant_id,task_type"),
                @Index(name = "ix_ai_request_audit_tenant_created", columnList = "tenant_id,created_at"),
                @Index(name = "ix_ai_request_audit_tenant_status", columnList = "tenant_id,status"),
                @Index(name = "ix_ai_request_audit_correlation", columnList = "correlation_id")
        }
)
public class AiRequestAuditEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "product_code", nullable = false)
    private String productCode;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "actor_app_user_id")
    private UUID actorAppUserId;

    @Column(name = "use_case_code")
    private String useCaseCode;

    @Column(name = "task_type", nullable = false)
    private String taskType;

    @Column(name = "prompt_template_code")
    private String promptTemplateCode;

    @Column(name = "prompt_template_version")
    private String promptTemplateVersion;

    @Column
    private String provider;

    @Column
    private String model;

    @Column(name = "request_hash")
    private String requestHash;

    @Column(name = "input_summary", columnDefinition = "text")
    private String inputSummary;

    @Column(name = "output_summary", columnDefinition = "text")
    private String outputSummary;

    @Column(nullable = false)
    private String status;

    @Column(precision = 19, scale = 4)
    private BigDecimal confidence;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Column(name = "total_tokens")
    private Long totalTokens;

    @Column(name = "estimated_cost", precision = 19, scale = 4)
    private BigDecimal estimatedCost;

    @Column(name = "fallback_used", nullable = false)
    private boolean fallbackUsed;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected AiRequestAuditEntity() {
    }

    public static AiRequestAuditEntity create(UUID id, String productCode, UUID tenantId, UUID actorAppUserId,
                                              String useCaseCode, String taskType, String promptTemplateCode,
                                              String promptTemplateVersion, String provider, String model,
                                              String requestHash, String inputSummary, String outputSummary,
                                              String status, BigDecimal confidence, Long latencyMs, Long inputTokens,
                                              Long outputTokens, Long totalTokens, BigDecimal estimatedCost,
                                              boolean fallbackUsed, String errorMessage, String correlationId) {
        AiRequestAuditEntity entity = new AiRequestAuditEntity();
        entity.id = id;
        entity.productCode = productCode;
        entity.tenantId = tenantId;
        entity.actorAppUserId = actorAppUserId;
        entity.useCaseCode = useCaseCode;
        entity.taskType = taskType;
        entity.promptTemplateCode = promptTemplateCode;
        entity.promptTemplateVersion = promptTemplateVersion;
        entity.provider = provider;
        entity.model = model;
        entity.requestHash = requestHash;
        entity.inputSummary = inputSummary;
        entity.outputSummary = outputSummary;
        entity.status = status;
        entity.confidence = confidence;
        entity.latencyMs = latencyMs;
        entity.inputTokens = inputTokens;
        entity.outputTokens = outputTokens;
        entity.totalTokens = totalTokens;
        entity.estimatedCost = estimatedCost;
        entity.fallbackUsed = fallbackUsed;
        entity.errorMessage = errorMessage;
        entity.correlationId = correlationId;
        entity.createdAt = OffsetDateTime.now();
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public String getProductCode() {
        return productCode;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getActorAppUserId() {
        return actorAppUserId;
    }

    public String getUseCaseCode() {
        return useCaseCode;
    }

    public String getTaskType() {
        return taskType;
    }

    public String getPromptTemplateCode() {
        return promptTemplateCode;
    }

    public String getPromptTemplateVersion() {
        return promptTemplateVersion;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getInputSummary() {
        return inputSummary;
    }

    public String getOutputSummary() {
        return outputSummary;
    }

    public String getStatus() {
        return status;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public Long getInputTokens() {
        return inputTokens;
    }

    public Long getOutputTokens() {
        return outputTokens;
    }

    public Long getTotalTokens() {
        return totalTokens;
    }

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
