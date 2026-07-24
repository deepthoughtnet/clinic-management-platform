package com.deepthoughtnet.clinic.commercial.catalog.db;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public class CommercialAddonFeatureId implements Serializable {
    @Column(name = "addon_id", nullable = false)
    private UUID addonId;

    @Column(name = "feature_id", nullable = false)
    private UUID featureId;

    protected CommercialAddonFeatureId() {
    }

    public CommercialAddonFeatureId(UUID addonId, UUID featureId) {
        this.addonId = addonId;
        this.featureId = featureId;
    }

    public UUID getAddonId() { return addonId; }
    public UUID getFeatureId() { return featureId; }
}
