package com.deepthoughtnet.clinic.api.patientportal.careai;

public record PatientPortalCareAiPlannerDecision(
        PatientPortalCareAiIntent intent,
        String doctorName,
        String speciality,
        String preferredDate,
        String preferredTimeWindow,
        PatientPortalCareAiPlannerConfirmationDecision confirmationDecision,
        String reason,
        boolean topicSwitch
) {
}
