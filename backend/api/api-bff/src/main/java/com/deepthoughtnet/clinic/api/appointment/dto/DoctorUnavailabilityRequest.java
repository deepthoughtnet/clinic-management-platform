package com.deepthoughtnet.clinic.api.appointment.dto;

import com.deepthoughtnet.clinic.appointment.service.model.DoctorUnavailabilityType;
import java.time.LocalDateTime;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DoctorUnavailabilityRequest(
        @NotNull
        LocalDateTime startAt,
        @NotNull
        LocalDateTime endAt,
        @NotNull
        DoctorUnavailabilityType type,
        @Size(max = 512)
        String reason,
        boolean active
) {
}
