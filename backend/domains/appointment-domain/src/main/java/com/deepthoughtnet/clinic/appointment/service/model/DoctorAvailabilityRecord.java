package com.deepthoughtnet.clinic.appointment.service.model;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DoctorAvailabilityRecord(
        UUID id,
        UUID tenantId,
        UUID doctorUserId,
        String doctorName,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        LocalTime breakStartTime,
        LocalTime breakEndTime,
        Integer consultationDurationMinutes,
        Integer maxPatientsPerSlot,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
