package com.deepthoughtnet.clinic.commercial.catalog.db;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "commercial_capability_modules")
public class CommercialCapabilityModuleEntity {
    @EmbeddedId
    private CommercialCapabilityModuleId id;

    @MapsId("capabilityId")
    @ManyToOne(optional = false)
    @JoinColumn(name = "capability_id", nullable = false)
    private CommercialCapabilityEntity capability;

    @MapsId("moduleId")
    @ManyToOne(optional = false)
    @JoinColumn(name = "module_id", nullable = false)
    private CommercialModuleEntity module;

    @Column(name = "included_by_default", nullable = false)
    private boolean includedByDefault;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    public CommercialCapabilityModuleEntity() {
    }

    public CommercialCapabilityModuleId getId() { return id; }
    public CommercialCapabilityEntity getCapability() { return capability; }
    public CommercialModuleEntity getModule() { return module; }
    public boolean isIncludedByDefault() { return includedByDefault; }
    public int getDisplayOrder() { return displayOrder; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public UUID getCreatedBy() { return createdBy; }
}
