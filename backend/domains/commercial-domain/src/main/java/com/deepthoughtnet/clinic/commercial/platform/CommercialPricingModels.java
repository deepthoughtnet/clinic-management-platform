package com.deepthoughtnet.clinic.commercial.platform;

import com.deepthoughtnet.clinic.commercial.platform.CommercialPricingEnums.AddonPurchaseType;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPricingEnums.BillingCycle;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPricingEnums.PricingStatus;
import com.deepthoughtnet.clinic.commercial.platform.CommercialPricingEnums.TaxModel;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class CommercialPricingModels {
    private CommercialPricingModels() {
    }

    public record PricingMeteredRateRequest(
            UUID limitDefinitionId,
            String includedQuantity,
            Boolean overageEnabled,
            String unitPrice,
            String unitName,
            String billingRounding
    ) {
    }

    public record PricingAddonRequest(
            UUID addonOfferId,
            AddonPurchaseType purchaseType,
            String monthlyPrice,
            String annualPrice,
            String oneTimePrice,
            Integer maxQuantity
    ) {
    }

    public record PricingSummary(
            UUID id,
            UUID publishedVersionId,
            String currency,
            BillingCycle billingCycle,
            String monthlyPrice,
            String annualPrice,
            String setupFee,
            Integer trialDays,
            TaxModel taxModel,
            String taxPercentage,
            boolean discountAllowed,
            PricingStatus status,
            OffsetDateTime createdAt,
            UUID createdBy,
            List<PricingMeteredRate> meteredRates,
            List<PricingAddonPricing> addonPricing
    ) {
    }

    public record PricingMeteredRate(
            UUID id,
            UUID pricingId,
            UUID limitDefinitionId,
            String limitCode,
            String limitName,
            String includedQuantity,
            boolean overageEnabled,
            String unitPrice,
            String unitName,
            String billingRounding,
            PricingStatus status
    ) {
    }

    public record PricingAddonPricing(
            UUID id,
            UUID pricingId,
            UUID addonOfferId,
            String addonCode,
            String addonName,
            AddonPurchaseType purchaseType,
            String monthlyPrice,
            String annualPrice,
            String oneTimePrice,
            Integer maxQuantity,
            PricingStatus status
    ) {
    }

    public record PricingValidationResult(
            String validationState,
            boolean readyToPublish,
            int blockingFindingCount,
            int warningFindingCount,
            List<String> findings,
            OffsetDateTime validatedAt
    ) {
    }

    public record PricingComparisonEntry(String code, String name, String detail) {
    }

    public record PricingComparisonResponse(
            UUID templateId,
            String templateCode,
            String templateName,
            String leftLabel,
            String rightLabel,
            List<PricingComparisonEntry> subscriptionPricing,
            List<PricingComparisonEntry> meteredRates,
            List<PricingComparisonEntry> addonPricing
    ) {
    }
}
