package com.deepthoughtnet.clinic.api.appointment.dto;

import com.deepthoughtnet.clinic.appointment.service.model.DoctorUnavailabilityType;
import java.time.LocalDateTime;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;

public record DoctorUnavailabilityRequest(
        @NotNull
        LocalDateTime startAt,
        @NotNull
        LocalDateTime endAt,
        @NotNull
        DoctorUnavailabilityType type,
        @Size(max = 250)
        String reason,
        boolean active
) {
        @AssertTrue(message = "End date/time must be after start date/time.")
        public boolean isValidTimeRange() {
                if (startAt == null || endAt == null) {
                        return true;
                }
                return endAt.isAfter(startAt);
        }
}
