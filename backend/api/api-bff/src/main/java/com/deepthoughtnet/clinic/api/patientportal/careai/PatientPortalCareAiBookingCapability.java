package com.deepthoughtnet.clinic.api.patientportal.careai;

/** Canonical provider facts used by the reducer after resolver validation. */
public record PatientPortalCareAiBookingCapability(
        String id,
        String name,
        String clinicId,
        String tenantId,
        String bookingMode
) {
}
