package com.deepthoughtnet.clinic.commercial.platform.db;

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
@Table(name = "commercial_plan_metered_rates", indexes = {
        @Index(name = "ix_commercial_plan_metered_rates_pricing", columnList = "pricing_id"),
        @Index(name = "ix_commercial_plan_metered_rates_limit", columnList = "limit_definition_id")
})
public class CommercialPlanMeteredRateEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pricing_id", nullable = false)
    private CommercialPlanPricingEntity pricing;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "limit_definition_id", nullable = false)
    private com.deepthoughtnet.clinic.commercial.catalog.db.CommercialLimitDefinitionEntity limitDefinition;

    @Column(name = "included_quantity", nullable = false, precision = 18, scale = 4)
    private BigDecimal includedQuantity;

    @Column(name = "overage_enabled", nullable = false)
    private boolean overageEnabled;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "unit_name", nullable = false, length = 128)
    private String unitName;

    @Column(name = "billing_rounding", length = 64)
    private String billingRounding;

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

    protected CommercialPlanMeteredRateEntity() {
    }

    public static CommercialPlanMeteredRateEntity create(
            UUID id,
            CommercialPlanPricingEntity pricing,
            com.deepthoughtnet.clinic.commercial.catalog.db.CommercialLimitDefinitionEntity limitDefinition,
            BigDecimal includedQuantity,
            boolean overageEnabled,
            BigDecimal unitPrice,
            String unitName,
            String billingRounding,
            PricingStatus status,
            OffsetDateTime createdAt,
            UUID createdBy
    ) {
        CommercialPlanMeteredRateEntity entity = new CommercialPlanMeteredRateEntity();
        entity.id = id;
        entity.pricing = pricing;
        entity.limitDefinition = limitDefinition;
        entity.includedQuantity = includedQuantity;
        entity.overageEnabled = overageEnabled;
        entity.unitPrice = unitPrice;
        entity.unitName = unitName;
        entity.billingRounding = billingRounding;
        entity.status = status;
        entity.createdAt = createdAt;
        entity.createdBy = createdBy;
        entity.version = 0L;
        return entity;
    }

    public UUID getId() { return id; }
    public CommercialPlanPricingEntity getPricing() { return pricing; }
    public com.deepthoughtnet.clinic.commercial.catalog.db.CommercialLimitDefinitionEntity getLimitDefinition() { return limitDefinition; }
    public BigDecimal getIncludedQuantity() { return includedQuantity; }
    public boolean isOverageEnabled() { return overageEnabled; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public String getUnitName() { return unitName; }
    public String getBillingRounding() { return billingRounding; }
    public PricingStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public UUID getCreatedBy() { return createdBy; }
    public long getVersion() { return version; }
}
