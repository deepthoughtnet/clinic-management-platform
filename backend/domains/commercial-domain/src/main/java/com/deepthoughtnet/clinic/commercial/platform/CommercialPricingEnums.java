package com.deepthoughtnet.clinic.commercial.platform;

public final class CommercialPricingEnums {
    private CommercialPricingEnums() {
    }

    public enum BillingCycle {
        MONTHLY,
        ANNUAL,
        QUARTERLY,
        ONE_TIME,
        TRIAL
    }

    public enum TaxModel {
        EXCLUSIVE,
        INCLUSIVE,
        NONE
    }

    public enum PricingStatus {
        DRAFT,
        PUBLISHED,
        RETIRED
    }

    public enum AddonPurchaseType {
        MONTHLY,
        ANNUAL,
        ONE_TIME
    }
}
