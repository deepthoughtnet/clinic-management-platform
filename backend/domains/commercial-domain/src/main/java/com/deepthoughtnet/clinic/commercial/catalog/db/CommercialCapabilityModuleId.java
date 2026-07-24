package com.deepthoughtnet.clinic.commercial.catalog.db;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public class CommercialCapabilityModuleId implements Serializable {
    @Column(name = "capability_id", nullable = false)
    private UUID capabilityId;

    @Column(name = "module_id", nullable = false)
    private UUID moduleId;

    protected CommercialCapabilityModuleId() {
    }

    public CommercialCapabilityModuleId(UUID capabilityId, UUID moduleId) {
        this.capabilityId = capabilityId;
        this.moduleId = moduleId;
    }

    public UUID getCapabilityId() { return capabilityId; }
    public UUID getModuleId() { return moduleId; }
}
