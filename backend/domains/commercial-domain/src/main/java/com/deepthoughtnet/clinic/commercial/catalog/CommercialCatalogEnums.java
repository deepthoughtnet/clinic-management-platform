package com.deepthoughtnet.clinic.commercial.catalog;

public final class CommercialCatalogEnums {
    private CommercialCatalogEnums() {
    }

    public enum Status {
        ACTIVE,
        INACTIVE,
        RETIRED
    }

    public enum AddonType {
        CAPABILITY,
        FEATURE,
        LIMIT_PACK,
        SERVICE
    }

    public enum LimitValueType {
        INTEGER,
        DECIMAL,
        BOOLEAN
    }

    public enum AggregationPeriod {
        NONE,
        DAILY,
        MONTHLY,
        ANNUAL
    }

    public enum EnforcementMode {
        INFORMATIONAL,
        SOFT,
        HARD
    }
}
