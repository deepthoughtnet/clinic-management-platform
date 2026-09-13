package com.deepthoughtnet.clinic.api.patientportal.careai;

public record PatientPortalCareAiPlannerDecision(
        PatientPortalCareAiIntent intent,
        String doctorName,
        String speciality,
        String preferredDate,
        String preferredTimeWindow,
        PatientPortalCareAiPlannerConfirmationDecision confirmationDecision,
        String reason,
        boolean topicSwitch,
        String sideTopic,
        PatientPortalCareAiDialogAct dialogAct,
        String correctionTarget,
        String alternativeTarget,
        Integer selectionOrdinal,
        String selectionTarget
) {
    public PatientPortalCareAiPlannerDecision(
            PatientPortalCareAiIntent intent,
            String doctorName,
            String speciality,
            String preferredDate,
            String preferredTimeWindow,
            PatientPortalCareAiPlannerConfirmationDecision confirmationDecision,
            String reason,
            boolean topicSwitch,
            String sideTopic
    ) {
        this(intent, doctorName, speciality, preferredDate, preferredTimeWindow, confirmationDecision,
                reason, topicSwitch, sideTopic, null, null, null, null, null);
    }
}
