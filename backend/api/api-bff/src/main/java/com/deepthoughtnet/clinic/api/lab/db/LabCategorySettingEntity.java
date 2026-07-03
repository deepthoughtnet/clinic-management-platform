package com.deepthoughtnet.clinic.api.lab.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "lab_category_settings",
        indexes = {
                @Index(name = "ix_lab_category_settings_tenant_active", columnList = "tenant_id,active"),
                @Index(name = "ix_lab_category_settings_tenant_display_order", columnList = "tenant_id,display_order")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_lab_category_settings_tenant_code", columnNames = {"tenant_id", "category_code"})
        }
)
public class LabCategorySettingEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "category_code", nullable = false, length = 64)
    private String categoryCode;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected LabCategorySettingEntity() {
    }

    public static LabCategorySettingEntity create(UUID tenantId, String categoryCode, String displayName, boolean active, Integer displayOrder) {
        LabCategorySettingEntity entity = new LabCategorySettingEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.categoryCode = categoryCode;
        entity.displayName = displayName;
        entity.active = active;
        entity.displayOrder = displayOrder;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public void update(String displayName, boolean active, Integer displayOrder) {
        this.displayName = displayName;
        this.active = active;
        this.displayOrder = displayOrder;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getCategoryCode() { return categoryCode; }
    public String getDisplayName() { return displayName; }
    public boolean isActive() { return active; }
    public Integer getDisplayOrder() { return displayOrder; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
