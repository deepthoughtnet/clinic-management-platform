package com.deepthoughtnet.clinic.commercial.catalog.db;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public class CommercialAddonLimitIncrementId implements Serializable {
    @Column(name = "addon_id", nullable = false)
    private UUID addonId;

    @Column(name = "limit_definition_id", nullable = false)
    private UUID limitDefinitionId;

    protected CommercialAddonLimitIncrementId() {
    }

    public CommercialAddonLimitIncrementId(UUID addonId, UUID limitDefinitionId) {
        this.addonId = addonId;
        this.limitDefinitionId = limitDefinitionId;
    }

    public UUID getAddonId() { return addonId; }
    public UUID getLimitDefinitionId() { return limitDefinitionId; }
}
