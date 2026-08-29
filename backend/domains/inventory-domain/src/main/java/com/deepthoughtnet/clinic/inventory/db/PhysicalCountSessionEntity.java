package com.deepthoughtnet.clinic.inventory.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "physical_count_sessions",
        indexes = {
                @Index(name = "ix_physical_count_sessions_tenant_status", columnList = "tenant_id,status"),
                @Index(name = "ix_physical_count_sessions_tenant_location", columnList = "tenant_id,location_id"),
                @Index(name = "ix_physical_count_sessions_tenant_updated", columnList = "tenant_id,updated_at")
        }
)
public class PhysicalCountSessionEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "session_name", nullable = false, length = 256)
    private String sessionName;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "location_name", nullable = false, length = 256)
    private String locationName;

    @Column(name = "scope", nullable = false, length = 32)
    private String scope;

    @Column(name = "scope_label", nullable = false, length = 128)
    private String scopeLabel;

    @Column(name = "reason", nullable = false, length = 64)
    private String reason;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "session_json", nullable = false, columnDefinition = "text")
    private String sessionJson;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected PhysicalCountSessionEntity() {
    }

    public static PhysicalCountSessionEntity create(
            UUID tenantId,
            UUID id,
            String sessionName,
            UUID locationId,
            String locationName,
            String scope,
            String scopeLabel,
            String reason,
            String status,
            String sessionJson,
            UUID createdBy,
            UUID updatedBy
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        PhysicalCountSessionEntity entity = new PhysicalCountSessionEntity();
        entity.id = id;
        entity.tenantId = tenantId;
        entity.sessionName = sessionName;
        entity.locationId = locationId;
        entity.locationName = locationName;
        entity.scope = scope;
        entity.scopeLabel = scopeLabel;
        entity.reason = reason;
        entity.status = status;
        entity.sessionJson = sessionJson;
        entity.createdBy = createdBy;
        entity.updatedBy = updatedBy;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void update(
            String sessionName,
            UUID locationId,
            String locationName,
            String scope,
            String scopeLabel,
            String reason,
            String status,
            String sessionJson,
            UUID updatedBy
    ) {
        this.sessionName = sessionName;
        this.locationId = locationId;
        this.locationName = locationName;
        this.scope = scope;
        this.scopeLabel = scopeLabel;
        this.reason = reason;
        this.status = status;
        this.sessionJson = sessionJson;
        this.updatedBy = updatedBy;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getSessionName() {
        return sessionName;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public String getLocationName() {
        return locationName;
    }

    public String getScope() {
        return scope;
    }

    public String getScopeLabel() {
        return scopeLabel;
    }

    public String getReason() {
        return reason;
    }

    public String getStatus() {
        return status;
    }

    public String getSessionJson() {
        return sessionJson;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
