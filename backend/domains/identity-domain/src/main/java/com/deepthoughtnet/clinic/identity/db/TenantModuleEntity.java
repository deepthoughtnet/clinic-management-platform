package com.deepthoughtnet.clinic.identity.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenant_modules", indexes = {
        @Index(name = "ix_tenant_modules_tenant", columnList = "tenant_id"),
        @Index(name = "ix_tenant_modules_tenant_enabled", columnList = "tenant_id,enabled")
})
public class TenantModuleEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "module_code", nullable = false, length = 64)
    private String moduleCode;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected TenantModuleEntity() {
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getModuleCode() { return moduleCode; }
    public boolean isEnabled() { return enabled; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
