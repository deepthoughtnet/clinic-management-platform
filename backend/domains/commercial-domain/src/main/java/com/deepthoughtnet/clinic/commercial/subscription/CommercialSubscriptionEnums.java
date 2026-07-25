package com.deepthoughtnet.clinic.commercial.subscription;

public final class CommercialSubscriptionEnums {
    private CommercialSubscriptionEnums() {
    }

    public enum SubscriptionStatus {
        DRAFT,
        ACTIVE,
        SCHEDULED,
        PAUSED,
        EXPIRED,
        CANCELLED,
        SUPERSEDED
    }

    public enum ValidationSeverity {
        INFO,
        WARNING,
        BLOCKING
    }

    public enum ValidationState {
        NOT_VALIDATED,
        VALID,
        INVALID,
        STALE
    }
}
