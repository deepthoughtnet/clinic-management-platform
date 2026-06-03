package com.deepthoughtnet.clinic.api.patientportal.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record PatientPortalAppointmentBookingRequest(
        String publicDoctorId,
        LocalDate appointmentDate,
        LocalTime appointmentTime,
        String reason
) {
}
