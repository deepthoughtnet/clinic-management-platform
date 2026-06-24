package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record PatientPortalCareAiAppointmentOption(
        UUID appointmentId,
        UUID doctorUserId,
        String doctorName,
        UUID tenantId,
        String clinicName,
        LocalDate appointmentDate,
        LocalTime appointmentTime,
        String status,
        String reason
) {
}
