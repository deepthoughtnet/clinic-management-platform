package com.deepthoughtnet.clinic.commercial.entitlement;

public final class CommercialEffectiveEntitlementEnums {
    private CommercialEffectiveEntitlementEnums() {
    }

    public enum OverrideTargetType {
        CAPABILITY,
        MODULE,
        FEATURE,
        LIMIT,
        ADD_ON
    }

    public enum OverrideOperation {
        ENABLE,
        DISABLE,
        SET_VALUE,
        SET_UNLIMITED,
        SET_ADDON_STATE
    }

    public enum OverrideStatus {
        DRAFT,
        PENDING_APPROVAL,
        CHANGES_REQUESTED,
        APPROVED,
        ACTIVE,
        SCHEDULED,
        EXPIRED,
        CANCELLED,
        SUPERSEDED
    }

    public enum SnapshotStatus {
        CURRENT,
        SUPERSEDED,
        INVALID,
        PENDING_REGENERATION
    }

    public enum GenerationReason {
        SUBSCRIPTION_ACTIVATED,
        SUBSCRIPTION_RESUMED,
        SUBSCRIPTION_REPLACED,
        SUBSCRIPTION_CANCELLED,
        SUBSCRIPTION_EXPIRED,
        OVERRIDE_CREATED,
        OVERRIDE_UPDATED,
        OVERRIDE_RETIRED,
        MANUAL_REGENERATE,
        BACKFILL,
        SHADOW_COMPARE
    }

    public enum SourceType {
        PLAN,
        ADD_ON,
        OVERRIDE
    }

    public enum ComparisonCategory {
        LEGACY_ONLY,
        COMMERCIAL_ONLY,
        MATCH,
        SNAPSHOT_MISSING,
        SNAPSHOT_INVALID,
        LIMIT_DIFFERENCE,
        DIFFERENT
    }

    public enum AddOnEffectiveState {
        INCLUDED,
        AVAILABLE_FOR_PURCHASE,
        UNAVAILABLE
    }
}
