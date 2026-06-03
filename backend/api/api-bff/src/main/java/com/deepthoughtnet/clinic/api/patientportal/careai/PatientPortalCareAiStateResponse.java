package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.util.List;

public record PatientPortalCareAiStateResponse(
        String language,
        String doctorName,
        String speciality,
        String preferredDate,
        String preferredTimeWindow,
        String suggestedSlot,
        boolean confirmationPending,
        boolean booked,
        String bookedAppointmentDate,
        String bookedAppointmentTime,
        String bookingStatus,
        boolean handoffRequired,
        String handoffReason,
        List<String> doctorOptions,
        List<String> slotOptions
) {
}
