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
        name = "inventory_locations",
        indexes = {
                @Index(name = "ix_inventory_locations_tenant_active", columnList = "tenant_id,active"),
                @Index(name = "ix_inventory_locations_tenant_default", columnList = "tenant_id,is_default")
        }
)
public class InventoryLocationEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "location_name", nullable = false, length = 256)
    private String locationName;

    @Column(name = "location_code", length = 64)
    private String locationCode;

    @Column(name = "location_type", nullable = false, length = 32)
    private String locationType;

    @Column(name = "is_default", nullable = false)
    private boolean defaultLocation;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected InventoryLocationEntity() {
    }

    public static InventoryLocationEntity create(UUID tenantId, String locationName, String locationCode, String locationType, boolean defaultLocation) {
        OffsetDateTime now = OffsetDateTime.now();
        InventoryLocationEntity entity = new InventoryLocationEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.locationName = locationName;
        entity.locationCode = locationCode;
        entity.locationType = locationType;
        entity.defaultLocation = defaultLocation;
        entity.active = true;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void update(String locationName, String locationCode, String locationType, boolean defaultLocation, boolean active) {
        this.locationName = locationName;
        this.locationCode = locationCode;
        this.locationType = locationType;
        this.defaultLocation = defaultLocation;
        this.active = active;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getLocationName() { return locationName; }
    public String getLocationCode() { return locationCode; }
    public String getLocationType() { return locationType; }
    public boolean isDefaultLocation() { return defaultLocation; }
    public boolean isActive() { return active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
