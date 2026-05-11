package com.deepthoughtnet.clinic.api.appointment.dto;

import com.deepthoughtnet.clinic.appointment.service.model.WaitlistStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

public record WaitlistResponse(
        String id,
        String tenantId,
        String patientId,
        String patientNumber,
        String patientName,
        String doctorUserId,
        String doctorName,
        LocalDate preferredDate,
        LocalTime preferredStartTime,
        LocalTime preferredEndTime,
        String reason,
        String notes,
        WaitlistStatus status,
        String bookedAppointmentId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
