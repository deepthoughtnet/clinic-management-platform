package com.deepthoughtnet.clinic.ai.orchestration.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "ai_prompt_templates",
        indexes = {
                @Index(name = "ix_ai_prompt_templates_code_scope", columnList = "template_code,tenant_id,product_code,version"),
                @Index(name = "ix_ai_prompt_templates_code_status", columnList = "template_code,status,updated_at")
        }
)
public class AiPromptTemplateEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "product_code")
    private String productCode;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "template_code", nullable = false)
    private String templateCode;

    @Column(nullable = false)
    private String version;

    @Column(name = "task_type", nullable = false)
    private String taskType;

    @Column(name = "system_prompt", nullable = false, columnDefinition = "text")
    private String systemPrompt;

    @Column(name = "user_prompt_template", nullable = false, columnDefinition = "text")
    private String userPromptTemplate;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected AiPromptTemplateEntity() {
    }

    public static AiPromptTemplateEntity create(UUID tenantId, String productCode, String templateCode, String version,
                                                String taskType, String systemPrompt, String userPromptTemplate,
                                                String status) {
        OffsetDateTime now = OffsetDateTime.now();
        AiPromptTemplateEntity entity = new AiPromptTemplateEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.productCode = productCode;
        entity.templateCode = templateCode;
        entity.version = version;
        entity.taskType = taskType;
        entity.systemPrompt = systemPrompt;
        entity.userPromptTemplate = userPromptTemplate;
        entity.status = status;
        entity.createdAt = now;
        entity.updatedAt = now;
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

    public String getTemplateCode() {
        return templateCode;
    }

    public String getVersion() {
        return version;
    }

    public String getTaskType() {
        return taskType;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getUserPromptTemplate() {
        return userPromptTemplate;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
