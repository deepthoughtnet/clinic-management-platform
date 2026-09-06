package com.deepthoughtnet.clinic.api.patientportal.dto;

import java.time.LocalDate;
import java.util.List;

public record PatientPortalDoctorAvailabilityResponse(
        LocalDate selectedDate,
        List<PatientPortalDoctorSlotResponse> slots,
        List<PatientPortalDoctorAvailabilityDayResponse> nextAvailable
) {
}
