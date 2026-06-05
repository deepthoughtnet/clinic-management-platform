package com.deepthoughtnet.clinic.api.patientportal.careai;

public interface PatientPortalCareAiPlanner {
    PatientPortalCareAiPlannerDecision plan(PatientPortalCareAiPlanningContext context);
}
