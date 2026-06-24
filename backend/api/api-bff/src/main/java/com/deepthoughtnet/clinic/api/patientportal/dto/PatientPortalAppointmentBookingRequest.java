package com.deepthoughtnet.clinic.api.patientportal.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record PatientPortalAppointmentBookingRequest(
        String publicDoctorId,
        String clinicSlug,
        String tenantId,
        String clinicId,
        LocalDate appointmentDate,
        LocalTime appointmentTime,
        String reason
) {
}
