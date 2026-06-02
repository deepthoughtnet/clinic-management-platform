package com.deepthoughtnet.clinic.api.patientportal.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

public record PatientPortalAppointmentResponse(
        String appointmentId,
        LocalDate appointmentDate,
        LocalTime appointmentTime,
        String doctorName,
        String reason,
        String type,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
