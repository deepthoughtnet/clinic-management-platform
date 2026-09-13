package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.util.List;

public record PatientPortalCareAiStateResponse(
        String language,
        String currentIntent,
        String workflowSubState,
        String doctorName,
        String speciality,
        String selectedAppointment,
        String preferredDate,
        String preferredTimeWindow,
        String suggestedSlot,
        boolean confirmationPending,
        boolean booked,
        boolean actionCompleted,
        String lastAction,
        String bookedAppointmentDate,
        String bookedAppointmentTime,
        String bookingStatus,
        boolean handoffRequired,
        String handoffReason,
        List<String> doctorOptions,
        List<String> appointmentOptions,
        List<String> slotOptions
) {
    public PatientPortalCareAiStateResponse(
            String language,
            String currentIntent,
            String doctorName,
            String speciality,
            String selectedAppointment,
            String preferredDate,
            String preferredTimeWindow,
            String suggestedSlot,
            boolean confirmationPending,
            boolean booked,
            boolean actionCompleted,
            String lastAction,
            String bookedAppointmentDate,
            String bookedAppointmentTime,
            String bookingStatus,
            boolean handoffRequired,
            String handoffReason,
            List<String> doctorOptions,
            List<String> appointmentOptions,
            List<String> slotOptions
    ) {
        this(
                language,
                currentIntent,
                null,
                doctorName,
                speciality,
                selectedAppointment,
                preferredDate,
                preferredTimeWindow,
                suggestedSlot,
                confirmationPending,
                booked,
                actionCompleted,
                lastAction,
                bookedAppointmentDate,
                bookedAppointmentTime,
                bookingStatus,
                handoffRequired,
                handoffReason,
                doctorOptions,
                appointmentOptions,
                slotOptions
        );
    }
}
