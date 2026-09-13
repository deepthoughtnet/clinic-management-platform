package com.deepthoughtnet.clinic.api.patientportal.careai;

/** One turn's mutation intent for a resolver-backed fact. */
record PatientPortalCareAiTurnFactDelta(
        PatientPortalCareAiFactMutation mutation,
        CanonicalResolution resolution
) {
    static PatientPortalCareAiTurnFactDelta unchanged() {
        return new PatientPortalCareAiTurnFactDelta(PatientPortalCareAiFactMutation.UNCHANGED, null);
    }

    static PatientPortalCareAiTurnFactDelta clear() {
        return new PatientPortalCareAiTurnFactDelta(PatientPortalCareAiFactMutation.CLEAR, null);
    }

    static PatientPortalCareAiTurnFactDelta set(CanonicalResolution resolution) {
        return new PatientPortalCareAiTurnFactDelta(PatientPortalCareAiFactMutation.SET, resolution);
    }

    boolean targeted() {
        return mutation != PatientPortalCareAiFactMutation.UNCHANGED;
    }

    boolean resolved() {
        return resolution != null && resolution.resolved();
    }

    CanonicalResolutionStatus status() {
        return resolution == null ? CanonicalResolutionStatus.UNRESOLVED : resolution.status();
    }

    String candidateText() {
        return resolution == null ? null : resolution.candidateText();
    }

    String canonicalValue() {
        return resolution == null ? null : resolution.canonicalValue();
    }

    java.util.List<String> candidateIds() {
        return resolution == null ? java.util.List.of() : resolution.candidateIds();
    }
}
