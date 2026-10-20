package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.time.LocalDate;

/** The read-only portion of a planner decision exposed to a channel adapter. */
public record PatientPortalCareAiVoicePlan(
        String intent,
        String tool,
        String query,
        String speciality,
        String clinic,
        String service,
        String location,
        String doctorId,
        LocalDate date,
        String clarification,
        boolean mutation,
        boolean closing
) {
    public static PatientPortalCareAiVoicePlan clarification(String text, String intent) {
        return new PatientPortalCareAiVoicePlan(intent, null, null, null, null, null, null, null, null, text, false, false);
    }

    public static PatientPortalCareAiVoicePlan mutation(String intent) {
        return new PatientPortalCareAiVoicePlan(intent, intent, null, null, null, null, null, null, null, null, true, false);
    }

    public static PatientPortalCareAiVoicePlan closing(String response) {
        return new PatientPortalCareAiVoicePlan("END_CONVERSATION", null, null, null, null, null,
                null, null, null, response, false, true);
    }
}
