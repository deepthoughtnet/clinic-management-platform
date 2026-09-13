package com.deepthoughtnet.clinic.api.patientportal.careai;

/** Safe, channel-neutral progress information for a pending deterministic skill. */
public record PatientPortalCareAiProgressEvent(
        String turnId,
        String skillExecutionId,
        String skillId,
        String progressKey,
        String state,
        String acknowledgement
) {
}
