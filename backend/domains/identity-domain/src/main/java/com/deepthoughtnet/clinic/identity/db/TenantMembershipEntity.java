package com.deepthoughtnet.clinic.identity.db;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenant_memberships", uniqueConstraints = {
        @UniqueConstraint(name = "uq_membership_tenant_user", columnNames = {"tenant_id","app_user_id"})
})
public class TenantMembershipEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "app_user_id", nullable = false)
    private UUID appUserId;

    @Column(nullable = false, length = 64)
    private String role; // ADMIN, AUDITOR, DISPATCHER, DRIVER, PARENT, VIEWER

    @Column(nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected TenantMembershipEntity() {}

    public static TenantMembershipEntity create(UUID tenantId, UUID appUserId, String role) {
        TenantMembershipEntity e = new TenantMembershipEntity();
        e.id = UUID.randomUUID();
        e.tenantId = tenantId;
        e.appUserId = appUserId;
        e.role = role;
        e.status = "ACTIVE";
        e.createdAt = OffsetDateTime.now();
        e.updatedAt = OffsetDateTime.now();
        return e;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getAppUserId() { return appUserId; }
    public String getRole() { return role; }
    public String getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setRole(String role) {
        this.role = role;
        this.updatedAt = OffsetDateTime.now();
    }

    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = OffsetDateTime.now();
    }
}
