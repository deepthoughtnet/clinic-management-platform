package com.deepthoughtnet.clinic.commercial.catalog.db;

import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.Status;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "commercial_capabilities")
public class CommercialCapabilityEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 512)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "standalone_allowed", nullable = false)
    private boolean standaloneAllowed;

    @Column(name = "addon_allowed", nullable = false)
    private boolean addonAllowed;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @OneToMany(mappedBy = "capability")
    private Set<CommercialCapabilityModuleEntity> modules = new LinkedHashSet<>();

    public CommercialCapabilityEntity() {
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Status getStatus() { return status; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isStandaloneAllowed() { return standaloneAllowed; }
    public boolean isAddonAllowed() { return addonAllowed; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public UUID getCreatedBy() { return createdBy; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public UUID getUpdatedBy() { return updatedBy; }
    public Set<CommercialCapabilityModuleEntity> getModules() { return modules; }
}
