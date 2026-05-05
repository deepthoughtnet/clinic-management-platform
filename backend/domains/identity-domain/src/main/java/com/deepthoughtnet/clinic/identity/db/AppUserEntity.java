package com.deepthoughtnet.clinic.identity.db;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "app_users", indexes = {
        @Index(name = "ix_app_users_tenant", columnList = "tenant_id"),
        @Index(name = "ix_app_users_sub", columnList = "keycloak_sub"),
        @Index(name = "ix_app_users_driver", columnList = "tenant_id,driver_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_users_tenant_sub", columnNames = {"tenant_id", "keycloak_sub"})
})
public class AppUserEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "keycloak_sub", nullable = false, length = 128)
    private String keycloakSub;

    @Column(length = 256)
    private String email;

    @Column(name = "display_name", length = 256)
    private String displayName;

    /**
     * ✅ NEW: link AppUser -> Driver (for driver mobile APIs)
     */
    @Column(name = "driver_id")
    private UUID driverId;

    @Column(nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected AppUserEntity() {}

    public static AppUserEntity create(UUID tenantId, String sub, String email, String displayName) {
        AppUserEntity u = new AppUserEntity();
        u.id = UUID.randomUUID();
        u.tenantId = tenantId;
        u.keycloakSub = sub;
        u.email = email;
        u.displayName = displayName;
        u.status = "ACTIVE";
        u.createdAt = OffsetDateTime.now();
        u.updatedAt = OffsetDateTime.now();
        return u;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getKeycloakSub() { return keycloakSub; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }

    public UUID getDriverId() { return driverId; }
    public String getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setDriverId(UUID driverId) {
        this.driverId = driverId;
        this.updatedAt = OffsetDateTime.now();
    }

    public void updateProfile(String email, String displayName) {
        this.email = email;
        this.displayName = displayName;
        this.updatedAt = OffsetDateTime.now();
    }
}
