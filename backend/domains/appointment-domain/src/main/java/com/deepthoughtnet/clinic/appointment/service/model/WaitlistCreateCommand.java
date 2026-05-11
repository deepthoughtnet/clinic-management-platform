package com.deepthoughtnet.clinic.appointment.service.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record WaitlistCreateCommand(
        UUID patientId,
        UUID doctorUserId,
        LocalDate preferredDate,
        LocalTime preferredStartTime,
        LocalTime preferredEndTime,
        String reason,
        String notes
) {
}
