package com.deepthoughtnet.clinic.api.medicationsafety;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record MedicationSafetyEvaluationRequest(
        UUID tenantId,
        UUID patientId,
        UUID consultationId,
        UUID prescriptionId,
        String prescriptionStatus,
        List<MedicationSafetyMedicationItem> proposedMedications,
        List<MedicationSafetyMedicationItem> currentMedications,
        AllergySnapshot allergies,
        List<String> activeConditions,
        RenalSnapshot renalContext,
        HepaticSnapshot hepaticContext,
        Integer ageYears,
        String gender,
        String pregnancyStatus,
        Map<String, Object> sourceVerificationMetadata
) {
    public record AllergySnapshot(
            String rawText,
            List<String> terms,
            boolean unknown,
            boolean noKnownAllergy,
            String verificationStatus
    ) {
    }

    public record RenalSnapshot(
            String creatinine,
            String creatinineDate,
            String egfr,
            String egfrDate,
            String verificationStatus,
            Integer stalenessDays,
            List<String> sourceDocumentIds
    ) {
    }

    public record HepaticSnapshot(
            String alt,
            String altDate,
            String ast,
            String astDate,
            String bilirubin,
            String bilirubinDate,
            String verificationStatus,
            Integer stalenessDays,
            List<String> sourceDocumentIds
    ) {
    }
}
