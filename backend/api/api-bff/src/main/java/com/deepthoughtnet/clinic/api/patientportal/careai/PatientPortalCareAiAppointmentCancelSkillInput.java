package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.util.UUID;

record PatientPortalCareAiAppointmentCancelSkillInput(
        UUID appointmentId,
        boolean confirmed,
        String idempotencyKey
) {
    PatientPortalCareAiAppointmentCancelSkillInput(UUID appointmentId, boolean confirmed) {
        this(appointmentId, confirmed, null);
    }
}
