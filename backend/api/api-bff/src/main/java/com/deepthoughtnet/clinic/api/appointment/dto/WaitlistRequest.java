package com.deepthoughtnet.clinic.api.appointment.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WaitlistRequest(
        @NotNull
        UUID patientId,
        UUID doctorUserId,
        @NotNull
        LocalDate preferredDate,
        LocalTime preferredStartTime,
        LocalTime preferredEndTime,
        @Size(max = 512)
        String reason,
        String notes
) {
}
