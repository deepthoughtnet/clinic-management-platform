package com.deepthoughtnet.clinic.api.patientportal.dto;

import java.time.LocalDate;
import java.util.List;

public record PatientPortalDoctorAvailabilityDayResponse(
        LocalDate appointmentDate,
        List<PatientPortalDoctorSlotResponse> slots
) {
}
