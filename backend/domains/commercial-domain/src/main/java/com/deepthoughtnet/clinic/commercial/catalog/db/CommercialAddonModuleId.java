package com.deepthoughtnet.clinic.commercial.catalog.db;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public class CommercialAddonModuleId implements Serializable {
    @Column(name = "addon_id", nullable = false)
    private UUID addonId;

    @Column(name = "module_id", nullable = false)
    private UUID moduleId;

    protected CommercialAddonModuleId() {
    }

    public CommercialAddonModuleId(UUID addonId, UUID moduleId) {
        this.addonId = addonId;
        this.moduleId = moduleId;
    }

    public UUID getAddonId() { return addonId; }
    public UUID getModuleId() { return moduleId; }
}
