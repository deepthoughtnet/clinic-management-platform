package com.deepthoughtnet.clinic.api.patientportal.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record PatientPortalAppointmentBookingRequest(
        String publicDoctorId,
        String clinicSlug,
        String tenantId,
        String clinicId,
        String bookingReference,
        String slotReference,
        LocalDate selectedDate,
        LocalDate appointmentDate,
        LocalTime appointmentTime,
        String consultationMode,
        String patientSubjectReference,
        String accountHolderReference,
        String relationshipToPatient,
        String patientNotes,
        String idempotencyKey,
        String notificationPreferences,
        String reason
) {
    public PatientPortalAppointmentBookingRequest(
            String publicDoctorId,
            String clinicSlug,
            String tenantId,
            String clinicId,
            LocalDate appointmentDate,
            LocalTime appointmentTime,
            String reason
    ) {
        this(
                publicDoctorId,
                clinicSlug,
                tenantId,
                clinicId,
                null,
                null,
                appointmentDate,
                appointmentDate,
                appointmentTime,
                null,
                null,
                null,
                null,
                reason,
                null,
                null,
                reason
        );
    }

    public PatientPortalAppointmentBookingRequest(
            String publicDoctorId,
            String clinicSlug,
            String tenantId,
            String clinicId,
            String bookingReference,
            LocalDate appointmentDate,
            LocalTime appointmentTime,
            String reason
    ) {
        this(
                publicDoctorId,
                clinicSlug,
                tenantId,
                clinicId,
                bookingReference,
                null,
                appointmentDate,
                appointmentDate,
                appointmentTime,
                null,
                null,
                null,
                null,
                reason,
                null,
                null,
                reason
        );
    }

    public PatientPortalAppointmentBookingRequest withIdempotencyKey(String idempotencyKey) {
        return new PatientPortalAppointmentBookingRequest(
                publicDoctorId,
                clinicSlug,
                tenantId,
                clinicId,
                bookingReference,
                slotReference,
                selectedDate,
                appointmentDate,
                appointmentTime,
                consultationMode,
                patientSubjectReference,
                accountHolderReference,
                relationshipToPatient,
                patientNotes,
                idempotencyKey,
                notificationPreferences,
                reason
        );
    }
}
