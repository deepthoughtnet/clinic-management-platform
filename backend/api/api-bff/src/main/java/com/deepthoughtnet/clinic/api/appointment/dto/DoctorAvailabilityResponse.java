package com.deepthoughtnet.clinic.api.appointment.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;

public record DoctorAvailabilityResponse(
        String id,
        String tenantId,
        String doctorUserId,
        String doctorName,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        Integer consultationDurationMinutes,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
