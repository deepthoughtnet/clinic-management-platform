package com.deepthoughtnet.clinic.api.patient.dto;

import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

public record AppointmentSummaryResponse(
        String id,
        String patientId,
        String patientNumber,
        String patientName,
        String doctorUserId,
        String doctorName,
        LocalDate appointmentDate,
        LocalTime appointmentTime,
        Integer tokenNumber,
        String reason,
        AppointmentType type,
        AppointmentPriority priority,
        AppointmentStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
