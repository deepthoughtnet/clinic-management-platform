package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.util.List;

public record PatientPortalCareAiPlanningContext(
        String language,
        String latestMessage,
        String currentIntent,
        boolean confirmationPending,
        String pendingAction,
        String requestedDoctorName,
        String selectedDoctorName,
        String requestedSpeciality,
        String selectedAppointmentLabel,
        String preferredDate,
        String preferredTimeWindow,
        String selectedSlot,
        List<String> missingFields,
        List<String> availableActions,
        List<String> doctorOptions,
        List<String> appointmentOptions,
        List<String> slotOptions,
        List<String> knownDoctorNames
) {
}
