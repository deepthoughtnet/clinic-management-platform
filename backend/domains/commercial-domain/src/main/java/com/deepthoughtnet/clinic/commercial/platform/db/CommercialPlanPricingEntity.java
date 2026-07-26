package com.deepthoughtnet.clinic.commercial.platform.db;

import com.deepthoughtnet.clinic.commercial.platform.CommercialPricingEnums.BillingCycle;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPricingEnums.PricingStatus;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPricingEnums.TaxModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "commercial_plan_pricing", indexes = {
        @Index(name = "ix_commercial_plan_pricing_version", columnList = "published_version_id"),
        @Index(name = "ix_commercial_plan_pricing_status", columnList = "status")
})
public class CommercialPlanPricingEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "published_version_id", nullable = false, unique = true)
    private CommercialPlanVersionEntity publishedVersion;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 32)
    private BillingCycle billingCycle;

    @Column(name = "monthly_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal monthlyPrice;

    @Column(name = "annual_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal annualPrice;

    @Column(name = "setup_fee", nullable = true, precision = 18, scale = 4)
    private BigDecimal setupFee;

    @Column(name = "trial_days", nullable = true)
    private Integer trialDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_model", nullable = false, length = 32)
    private TaxModel taxModel;

    @Column(name = "tax_percentage", nullable = true, precision = 9, scale = 4)
    private BigDecimal taxPercentage;

    @Column(name = "discount_allowed", nullable = false)
    private boolean discountAllowed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PricingStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @OneToMany(mappedBy = "pricing", fetch = FetchType.LAZY)
    private List<CommercialPlanMeteredRateEntity> meteredRates = new ArrayList<>();

    @OneToMany(mappedBy = "pricing", fetch = FetchType.LAZY)
    private List<CommercialPlanAddonPricingEntity> addonPricing = new ArrayList<>();

    @Version
    @Column(nullable = false)
    private long version;

    protected CommercialPlanPricingEntity() {
    }

    public static CommercialPlanPricingEntity create(
            UUID id,
            CommercialPlanVersionEntity publishedVersion,
            String currency,
            BillingCycle billingCycle,
            BigDecimal monthlyPrice,
            BigDecimal annualPrice,
            BigDecimal setupFee,
            Integer trialDays,
            TaxModel taxModel,
            BigDecimal taxPercentage,
            boolean discountAllowed,
            PricingStatus status,
            OffsetDateTime createdAt,
            UUID createdBy
    ) {
        CommercialPlanPricingEntity entity = new CommercialPlanPricingEntity();
        entity.id = id;
        entity.publishedVersion = publishedVersion;
        entity.currency = currency;
        entity.billingCycle = billingCycle;
        entity.monthlyPrice = monthlyPrice;
        entity.annualPrice = annualPrice;
        entity.setupFee = setupFee;
        entity.trialDays = trialDays;
        entity.taxModel = taxModel;
        entity.taxPercentage = taxPercentage;
        entity.discountAllowed = discountAllowed;
        entity.status = status;
        entity.createdAt = createdAt;
        entity.createdBy = createdBy;
        entity.version = 0L;
        return entity;
    }

    public UUID getId() { return id; }
    public CommercialPlanVersionEntity getPublishedVersion() { return publishedVersion; }
    public String getCurrency() { return currency; }
    public BillingCycle getBillingCycle() { return billingCycle; }
    public BigDecimal getMonthlyPrice() { return monthlyPrice; }
    public BigDecimal getAnnualPrice() { return annualPrice; }
    public BigDecimal getSetupFee() { return setupFee; }
    public Integer getTrialDays() { return trialDays; }
    public TaxModel getTaxModel() { return taxModel; }
    public BigDecimal getTaxPercentage() { return taxPercentage; }
    public boolean isDiscountAllowed() { return discountAllowed; }
    public PricingStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public UUID getCreatedBy() { return createdBy; }
    public List<CommercialPlanMeteredRateEntity> getMeteredRates() { return meteredRates; }
    public List<CommercialPlanAddonPricingEntity> getAddonPricing() { return addonPricing; }
    public long getVersion() { return version; }
}
