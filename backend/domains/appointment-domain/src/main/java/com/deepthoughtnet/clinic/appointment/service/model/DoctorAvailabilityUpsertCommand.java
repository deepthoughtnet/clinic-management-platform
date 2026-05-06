package com.deepthoughtnet.clinic.appointment.service.model;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record DoctorAvailabilityUpsertCommand(
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        Integer consultationDurationMinutes,
        boolean active
) {
}
