package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.time.LocalDate;
import java.util.List;

/** Bounded candidates tied to one exact search criteria version. */
public record PatientPortalCareAiCandidateContext(
        String type,
        long criteriaVersion,
        List<String> candidateIds,
        String doctorId,
        String clinicId,
        LocalDate date,
        String timeWindow
) {
    public PatientPortalCareAiCandidateContext {
        candidateIds = candidateIds == null ? List.of() : List.copyOf(candidateIds);
    }
}
