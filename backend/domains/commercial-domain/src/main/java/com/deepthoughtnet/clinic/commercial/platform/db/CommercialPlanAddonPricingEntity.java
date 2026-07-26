package com.deepthoughtnet.clinic.commercial.platform.db;

import com.deepthoughtnet.clinic.commercial.platform.CommercialPricingEnums.AddonPurchaseType;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPricingEnums.PricingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "commercial_plan_addon_pricing", indexes = {
        @Index(name = "ix_commercial_plan_addon_pricing_pricing", columnList = "pricing_id"),
        @Index(name = "ix_commercial_plan_addon_pricing_addon", columnList = "addon_offer_id")
})
public class CommercialPlanAddonPricingEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pricing_id", nullable = false)
    private CommercialPlanPricingEntity pricing;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "addon_offer_id", nullable = false)
    private com.deepthoughtnet.clinic.commercial.catalog.db.CommercialAddonOfferEntity addonOffer;

    @Enumerated(EnumType.STRING)
    @Column(name = "purchase_type", nullable = false, length = 32)
    private AddonPurchaseType purchaseType;

    @Column(name = "monthly_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal monthlyPrice;

    @Column(name = "annual_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal annualPrice;

    @Column(name = "one_time_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal oneTimePrice;

    @Column(name = "max_quantity")
    private Integer maxQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PricingStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Version
    @Column(nullable = false)
    private long version;

    protected CommercialPlanAddonPricingEntity() {
    }

    public static CommercialPlanAddonPricingEntity create(
            UUID id,
            CommercialPlanPricingEntity pricing,
            com.deepthoughtnet.clinic.commercial.catalog.db.CommercialAddonOfferEntity addonOffer,
            AddonPurchaseType purchaseType,
            BigDecimal monthlyPrice,
            BigDecimal annualPrice,
            BigDecimal oneTimePrice,
            Integer maxQuantity,
            PricingStatus status,
            OffsetDateTime createdAt,
            UUID createdBy
    ) {
        CommercialPlanAddonPricingEntity entity = new CommercialPlanAddonPricingEntity();
        entity.id = id;
        entity.pricing = pricing;
        entity.addonOffer = addonOffer;
        entity.purchaseType = purchaseType;
        entity.monthlyPrice = monthlyPrice;
        entity.annualPrice = annualPrice;
        entity.oneTimePrice = oneTimePrice;
        entity.maxQuantity = maxQuantity;
        entity.status = status;
        entity.createdAt = createdAt;
        entity.createdBy = createdBy;
        entity.version = 0L;
        return entity;
    }

    public UUID getId() { return id; }
    public CommercialPlanPricingEntity getPricing() { return pricing; }
    public com.deepthoughtnet.clinic.commercial.catalog.db.CommercialAddonOfferEntity getAddonOffer() { return addonOffer; }
    public AddonPurchaseType getPurchaseType() { return purchaseType; }
    public BigDecimal getMonthlyPrice() { return monthlyPrice; }
    public BigDecimal getAnnualPrice() { return annualPrice; }
    public BigDecimal getOneTimePrice() { return oneTimePrice; }
    public Integer getMaxQuantity() { return maxQuantity; }
    public PricingStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public UUID getCreatedBy() { return createdBy; }
    public long getVersion() { return version; }
}
