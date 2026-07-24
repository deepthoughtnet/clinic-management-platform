package com.deepthoughtnet.clinic.commercial.catalog.db;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "commercial_addon_features")
public class CommercialAddonFeatureEntity {
    @EmbeddedId
    private CommercialAddonFeatureId id;

    @MapsId("addonId")
    @ManyToOne(optional = false)
    @JoinColumn(name = "addon_id", nullable = false)
    private CommercialAddonOfferEntity addon;

    @MapsId("featureId")
    @ManyToOne(optional = false)
    @JoinColumn(name = "feature_id", nullable = false)
    private CommercialFeatureEntity feature;

    public CommercialAddonFeatureEntity() {
    }

    public CommercialAddonFeatureId getId() { return id; }
    public CommercialAddonOfferEntity getAddon() { return addon; }
    public CommercialFeatureEntity getFeature() { return feature; }
}
