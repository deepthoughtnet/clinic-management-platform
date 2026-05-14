package com.deepthoughtnet.clinic.ai.orchestration.platform.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Tool definition registry for future agentic workflows. */
@Entity
@Table(name = "ai_tool_definitions")
public class AiToolDefinitionEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "tool_key", nullable = false)
    private String toolKey;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column
    private String category;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "risk_level")
    private String riskLevel;

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval;

    @Column(name = "input_schema_json", columnDefinition = "text")
    private String inputSchemaJson;

    @Column(name = "output_schema_json", columnDefinition = "text")
    private String outputSchemaJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected AiToolDefinitionEntity() {}

    public static AiToolDefinitionEntity create(UUID tenantId, String toolKey, String name, String description,
                                                String category, boolean enabled, String riskLevel,
                                                boolean requiresApproval, String inputSchemaJson,
                                                String outputSchemaJson) {
        OffsetDateTime now = OffsetDateTime.now();
        AiToolDefinitionEntity entity = new AiToolDefinitionEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.toolKey = toolKey;
        entity.name = name;
        entity.description = description;
        entity.category = category;
        entity.enabled = enabled;
        entity.riskLevel = riskLevel;
        entity.requiresApproval = requiresApproval;
        entity.inputSchemaJson = inputSchemaJson;
        entity.outputSchemaJson = outputSchemaJson;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getToolKey() { return toolKey; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public boolean isEnabled() { return enabled; }
    public String getRiskLevel() { return riskLevel; }
    public boolean isRequiresApproval() { return requiresApproval; }
    public String getInputSchemaJson() { return inputSchemaJson; }
    public String getOutputSchemaJson() { return outputSchemaJson; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
