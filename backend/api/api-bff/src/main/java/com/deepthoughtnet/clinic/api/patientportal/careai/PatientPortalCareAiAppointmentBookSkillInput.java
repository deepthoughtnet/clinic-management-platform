package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.time.LocalDate;
import java.time.LocalTime;

record PatientPortalCareAiAppointmentBookSkillInput(
        String doctorId,
        String clinicSlug,
        String tenantId,
        String clinicId,
        String bookingReference,
        String bookingMode,
        LocalDate date,
        LocalTime time,
        String reason,
        boolean confirmed,
        String idempotencyKey
) {
    PatientPortalCareAiAppointmentBookSkillInput(
            String doctorId,
            String clinicSlug,
            String tenantId,
            String clinicId,
            String bookingReference,
            String bookingMode,
            LocalDate date,
            LocalTime time,
            String reason,
            boolean confirmed
    ) {
        this(doctorId, clinicSlug, tenantId, clinicId, bookingReference, bookingMode, date, time, reason, confirmed, null);
    }
}
