package com.deepthoughtnet.clinic.commercial.catalog.db;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "commercial_addon_capabilities")
public class CommercialAddonCapabilityEntity {
    @EmbeddedId
    private CommercialAddonCapabilityId id;

    @MapsId("addonId")
    @ManyToOne(optional = false)
    @JoinColumn(name = "addon_id", nullable = false)
    private CommercialAddonOfferEntity addon;

    @MapsId("capabilityId")
    @ManyToOne(optional = false)
    @JoinColumn(name = "capability_id", nullable = false)
    private CommercialCapabilityEntity capability;

    public CommercialAddonCapabilityEntity() {
    }

    public CommercialAddonCapabilityId getId() { return id; }
    public CommercialAddonOfferEntity getAddon() { return addon; }
    public CommercialCapabilityEntity getCapability() { return capability; }
}
