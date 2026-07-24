package com.deepthoughtnet.clinic.commercial.catalog.db;

import com.deepthoughtnet.clinic.commercial.catalog.CommercialCatalogEnums.AddonType;
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
@Table(name = "commercial_addon_offers")
public class CommercialAddonOfferEntity {
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

    @Enumerated(EnumType.STRING)
    @Column(name = "addon_type", nullable = false, length = 32)
    private AddonType addonType;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean repeatable;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @OneToMany(mappedBy = "addon")
    private Set<CommercialAddonCapabilityEntity> capabilities = new LinkedHashSet<>();

    @OneToMany(mappedBy = "addon")
    private Set<CommercialAddonModuleEntity> modules = new LinkedHashSet<>();

    @OneToMany(mappedBy = "addon")
    private Set<CommercialAddonFeatureEntity> features = new LinkedHashSet<>();

    @OneToMany(mappedBy = "addon")
    private Set<CommercialAddonLimitIncrementEntity> limitIncrements = new LinkedHashSet<>();

    public CommercialAddonOfferEntity() {
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Status getStatus() { return status; }
    public AddonType getAddonType() { return addonType; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isRepeatable() { return repeatable; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public UUID getCreatedBy() { return createdBy; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public UUID getUpdatedBy() { return updatedBy; }
    public Set<CommercialAddonCapabilityEntity> getCapabilities() { return capabilities; }
    public Set<CommercialAddonModuleEntity> getModules() { return modules; }
    public Set<CommercialAddonFeatureEntity> getFeatures() { return features; }
    public Set<CommercialAddonLimitIncrementEntity> getLimitIncrements() { return limitIncrements; }
}
