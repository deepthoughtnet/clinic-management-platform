package com.deepthoughtnet.clinic.api.appointment.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;
import jakarta.validation.constraints.AssertTrue;
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
        @NotNull
        @Positive
        Integer maxPatientsPerSlot,
        boolean active
) {
        @AssertTrue(message = "End time must be after start time.")
        public boolean isValidTimeRange() {
                if (startTime == null || endTime == null) {
                        return true;
                }
                return endTime.isAfter(startTime);
        }

        @AssertTrue(message = "Break end time must be after break start time.")
        public boolean isValidBreakRange() {
                if (breakStartTime == null && breakEndTime == null) {
                        return true;
                }
                if (breakStartTime == null || breakEndTime == null) {
                        return false;
                }
                return breakEndTime.isAfter(breakStartTime);
        }
}
