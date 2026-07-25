package com.deepthoughtnet.clinic.commercial.platform;

public final class CommercialPlatformEnums {
    private CommercialPlatformEnums() {
    }

    public enum TemplateStatus {
        DRAFT,
        ACTIVE,
        RETIRED
    }

    public enum DraftStatus {
        DRAFT,
        READY_TO_PUBLISH,
        BLOCKED
    }

    public enum PublicationStatus {
        PUBLISHED,
        RETIRED
    }

    public enum TargetSegment {
        SOLO,
        SMALL_CLINIC,
        MULTI_DOCTOR_CLINIC,
        SPECIALITY_CLINIC,
        DIAGNOSTIC_CENTER,
        PHARMACY,
        ENTERPRISE,
        CUSTOM
    }

    public enum SelectionState {
        INCLUDED,
        AVAILABLE,
        UNAVAILABLE
    }

    public enum SelectionSource {
        EXPLICIT,
        INHERITED
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
