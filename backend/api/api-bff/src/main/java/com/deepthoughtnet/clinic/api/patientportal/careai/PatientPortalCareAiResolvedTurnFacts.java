package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.time.LocalDate;
import java.time.LocalTime;

/** Resolver output passed to the shadow reducer; no raw transcript is retained. */
record PatientPortalCareAiResolvedTurnFacts(
        PatientPortalCareAiTurnFactDelta doctor,
        PatientPortalCareAiTurnFactDelta speciality,
        PatientPortalCareAiTurnFactDelta clinic,
        PatientPortalCareAiTurnFactDelta selection,
        PatientPortalCareAiTurnFactDelta date,
        PatientPortalCareAiTurnFactDelta timeWindow,
        PatientPortalCareAiTurnFactDelta exactTime
) {
    PatientPortalCareAiResolvedTurnFacts(
            CanonicalResolution doctor,
            CanonicalResolution speciality,
            CanonicalResolution clinic,
            CanonicalResolution selection,
            CanonicalResolution date,
            CanonicalResolution timeWindow,
            CanonicalResolution exactTime
    ) {
        this(delta(doctor), delta(speciality), delta(clinic), delta(selection), delta(date), delta(timeWindow), delta(exactTime));
    }

    LocalDate resolvedDate() {
        return resolvedValue(date) == null ? null : LocalDate.parse(resolvedValue(date));
    }

    LocalTime resolvedExactTime() {
        return resolvedValue(exactTime) == null ? null : LocalTime.parse(resolvedValue(exactTime));
    }

    String resolvedTimeWindow() {
        return resolvedValue(timeWindow);
    }

    String resolvedSlot() {
        return resolvedValue(selection);
    }

    PatientPortalCareAiBookingCapability resolvedDoctor() {
        if (doctor == null || !doctor.resolved() || doctor.resolution().candidateIds().size() != 1) {
            return null;
        }
        return new PatientPortalCareAiBookingCapability(
                doctor.resolution().candidateIds().getFirst(), doctor.resolution().canonicalValue(), null, null, null);
    }

    String summary() {
        return "doctor=" + status(doctor) + ",speciality=" + status(speciality)
                + ",clinic=" + status(clinic) + ",selection=" + status(selection)
                + ",date=" + status(date) + ",timeWindow=" + status(timeWindow)
                + ",exactTime=" + status(exactTime);
    }

    private String status(CanonicalResolution resolution) {
        return resolution == null ? "UNCHANGED" : resolution.status().name();
    }

    private String status(PatientPortalCareAiTurnFactDelta delta) {
        if (delta == null || delta.mutation() == PatientPortalCareAiFactMutation.UNCHANGED) return "UNCHANGED";
        return delta.mutation() + ":" + status(delta.resolution());
    }

    private String resolvedValue(PatientPortalCareAiTurnFactDelta delta) {
        return delta != null && delta.resolved() ? delta.resolution().canonicalValue() : null;
    }

    private static PatientPortalCareAiTurnFactDelta delta(CanonicalResolution resolution) {
        return resolution == null || resolution.candidateText() == null
                ? PatientPortalCareAiTurnFactDelta.unchanged()
                : PatientPortalCareAiTurnFactDelta.set(resolution);
    }
}
