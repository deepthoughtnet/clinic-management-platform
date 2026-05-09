package com.deepthoughtnet.clinic.api.appointment.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DoctorAvailabilityRequest(
        @NotNull
        DayOfWeek dayOfWeek,
        @NotNull
        LocalTime startTime,
        @NotNull
        LocalTime endTime,
        LocalTime breakStartTime,
        LocalTime breakEndTime,
        @NotNull
        @Positive
        Integer consultationDurationMinutes,
        Integer maxPatientsPerSlot,
        boolean active
) {
}
