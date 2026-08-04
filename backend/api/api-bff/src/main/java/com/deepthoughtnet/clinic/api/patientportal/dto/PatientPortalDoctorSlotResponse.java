package com.deepthoughtnet.clinic.api.patientportal.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record PatientPortalDoctorSlotResponse(
        String slotReference,
        LocalDate appointmentDate,
        LocalTime slotTime,
        LocalTime slotEndTime,
        String status,
        boolean selectable
) {
    public PatientPortalDoctorSlotResponse(
            LocalDate appointmentDate,
            LocalTime slotTime,
            LocalTime slotEndTime,
            String status,
            boolean selectable
    ) {
        this(null, appointmentDate, slotTime, slotEndTime, status, selectable);
    }
}
