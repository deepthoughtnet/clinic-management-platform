package com.deepthoughtnet.clinic.commercial.catalog.db;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public class CommercialAddonCapabilityId implements Serializable {
    @Column(name = "addon_id", nullable = false)
    private UUID addonId;

    @Column(name = "capability_id", nullable = false)
    private UUID capabilityId;

    protected CommercialAddonCapabilityId() {
    }

    public CommercialAddonCapabilityId(UUID addonId, UUID capabilityId) {
        this.addonId = addonId;
        this.capabilityId = capabilityId;
    }

    public UUID getAddonId() { return addonId; }
    public UUID getCapabilityId() { return capabilityId; }
}
