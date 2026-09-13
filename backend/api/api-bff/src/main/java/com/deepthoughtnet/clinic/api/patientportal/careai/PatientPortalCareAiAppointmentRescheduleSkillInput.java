package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

record PatientPortalCareAiAppointmentRescheduleSkillInput(
        UUID appointmentId,
        LocalDate date,
        LocalTime time,
        String reason,
        boolean confirmed,
        String idempotencyKey
) {
    PatientPortalCareAiAppointmentRescheduleSkillInput(UUID appointmentId, LocalDate date, LocalTime time, String reason, boolean confirmed) {
        this(appointmentId, date, time, reason, confirmed, null);
    }
}
