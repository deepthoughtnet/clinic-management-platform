package com.deepthoughtnet.clinic.api.appointment.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record DoctorAvailabilityRequest(
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        Integer consultationDurationMinutes,
        boolean active
) {
}
