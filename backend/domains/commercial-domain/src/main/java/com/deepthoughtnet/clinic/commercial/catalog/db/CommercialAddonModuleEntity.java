package com.deepthoughtnet.clinic.commercial.catalog.db;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "commercial_addon_modules")
public class CommercialAddonModuleEntity {
    @EmbeddedId
    private CommercialAddonModuleId id;

    @MapsId("addonId")
    @ManyToOne(optional = false)
    @JoinColumn(name = "addon_id", nullable = false)
    private CommercialAddonOfferEntity addon;

    @MapsId("moduleId")
    @ManyToOne(optional = false)
    @JoinColumn(name = "module_id", nullable = false)
    private CommercialModuleEntity module;

    public CommercialAddonModuleEntity() {
    }

    public CommercialAddonModuleId getId() { return id; }
    public CommercialAddonOfferEntity getAddon() { return addon; }
    public CommercialModuleEntity getModule() { return module; }
}
