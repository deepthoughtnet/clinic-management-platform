package com.deepthoughtnet.clinic.api.patientportal.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record PatientPortalDoctorSlotResponse(
        LocalDate appointmentDate,
        LocalTime slotTime,
        LocalTime slotEndTime,
        String status,
        boolean selectable
) {
}
