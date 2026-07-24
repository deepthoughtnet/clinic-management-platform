package com.deepthoughtnet.clinic.commercial.catalog.db;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "commercial_addon_limit_increments")
public class CommercialAddonLimitIncrementEntity {
    @EmbeddedId
    private CommercialAddonLimitIncrementId id;

    @MapsId("addonId")
    @ManyToOne(optional = false)
    @JoinColumn(name = "addon_id", nullable = false)
    private CommercialAddonOfferEntity addon;

    @MapsId("limitDefinitionId")
    @ManyToOne(optional = false)
    @JoinColumn(name = "limit_definition_id", nullable = false)
    private CommercialLimitDefinitionEntity limitDefinition;

    @Column(name = "increment_value", nullable = false)
    private BigDecimal incrementValue;

    public CommercialAddonLimitIncrementEntity() {
    }

    public CommercialAddonLimitIncrementId getId() { return id; }
    public CommercialAddonOfferEntity getAddon() { return addon; }
    public CommercialLimitDefinitionEntity getLimitDefinition() { return limitDefinition; }
    public BigDecimal getIncrementValue() { return incrementValue; }
}
