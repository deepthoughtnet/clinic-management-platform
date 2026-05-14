package com.deepthoughtnet.clinic.carepilot.template.db;

import com.deepthoughtnet.clinic.carepilot.template.model.TemplateCategory;
import com.deepthoughtnet.clinic.carepilot.template.model.TemplateChannel;
import com.deepthoughtnet.clinic.carepilot.template.model.TemplateType;
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
 * Generic tenant-scoped template persistence model for operational messaging use-cases.
 */
@Entity
@Table(name = "carepilot_templates", indexes = {
        @Index(name = "ix_cp_templates_tenant_type", columnList = "tenant_id,template_type"),
        @Index(name = "ix_cp_templates_tenant_category", columnList = "tenant_id,category"),
        @Index(name = "ix_cp_templates_tenant_active", columnList = "tenant_id,is_active")
})
public class CareTemplateEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 140)
    private String name;

    @Column(length = 512)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "template_type", nullable = false, length = 40)
    private TemplateType templateType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TemplateChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TemplateCategory category;

    @Column(name = "subject", length = 300)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "variables_json", columnDefinition = "text")
    private String variablesJson;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "is_system_template", nullable = false)
    private boolean systemTemplate;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected CareTemplateEntity() {}

    public static CareTemplateEntity create(
            UUID tenantId,
            String name,
            String description,
            TemplateType templateType,
            TemplateChannel channel,
            TemplateCategory category,
            String subject,
            String body,
            String variablesJson,
            boolean active,
            boolean systemTemplate,
            UUID actorAppUserId
    ) {
        CareTemplateEntity entity = new CareTemplateEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.name = name;
        entity.description = description;
        entity.templateType = templateType;
        entity.channel = channel;
        entity.category = category;
        entity.subject = subject;
        entity.body = body;
        entity.variablesJson = variablesJson;
        entity.active = active;
        entity.systemTemplate = systemTemplate;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        entity.createdBy = actorAppUserId;
        entity.updatedBy = actorAppUserId;
        return entity;
    }

    public void update(
            String name,
            String description,
            TemplateType templateType,
            TemplateChannel channel,
            TemplateCategory category,
            String subject,
            String body,
            String variablesJson,
            Boolean active,
            UUID actorAppUserId
    ) {
        if (name != null) this.name = name;
        if (description != null) this.description = description;
        if (templateType != null) this.templateType = templateType;
        if (channel != null) this.channel = channel;
        if (category != null) this.category = category;
        if (subject != null) this.subject = subject;
        if (body != null) this.body = body;
        if (variablesJson != null) this.variablesJson = variablesJson;
        if (active != null) this.active = active;
        this.updatedAt = OffsetDateTime.now();
        this.updatedBy = actorAppUserId;
    }

    public void setActive(boolean active, UUID actorAppUserId) {
        this.active = active;
        this.updatedAt = OffsetDateTime.now();
        this.updatedBy = actorAppUserId;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public TemplateType getTemplateType() { return templateType; }
    public TemplateChannel getChannel() { return channel; }
    public TemplateCategory getCategory() { return category; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public String getVariablesJson() { return variablesJson; }
    public boolean isActive() { return active; }
    public boolean isSystemTemplate() { return systemTemplate; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getUpdatedBy() { return updatedBy; }
}
