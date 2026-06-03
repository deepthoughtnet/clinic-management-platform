package com.deepthoughtnet.clinic.api.patientportal.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record PatientPortalAppointmentConfirmationResponse(
        LocalDate appointmentDate,
        LocalTime appointmentTime,
        String doctorName,
        String clinicName,
        String source,
        String status,
        String reason,
        String message
) {
}
