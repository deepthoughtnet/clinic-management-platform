package com.deepthoughtnet.clinic.api.clinicalmemory.model;

import java.util.List;

public record PatientLongitudinalMemoryProfile(
        List<LongitudinalConceptSnapshot> knownConditions,
        List<LongitudinalConceptSnapshot> longTermMedications,
        LongitudinalConceptSnapshot latestHbA1c,
        LongitudinalConceptSnapshot latestBloodSugar,
        List<LongitudinalConceptSnapshot> latestLipidSummary,
        LongitudinalConceptSnapshot latestBloodPressure,
        LongitudinalConceptSnapshot latestBmi,
        List<LongitudinalConceptSnapshot> riskFlags,
        List<LongitudinalConceptSnapshot> history,
        String mostRecentLaboratorySummary
) {
}
