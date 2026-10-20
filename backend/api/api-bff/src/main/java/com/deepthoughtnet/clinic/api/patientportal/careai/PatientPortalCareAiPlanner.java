package com.deepthoughtnet.clinic.api.patientportal.careai;

public interface PatientPortalCareAiPlanner {
    PatientPortalCareAiPlannerDecision plan(PatientPortalCareAiPlanningContext context);

    default PatientPortalCareAiPlannerDecision plan(
            PatientPortalCareAiPlanningContext context,
            AivaConversationContext conversationContext
    ) {
        return plan(context);
    }
}
