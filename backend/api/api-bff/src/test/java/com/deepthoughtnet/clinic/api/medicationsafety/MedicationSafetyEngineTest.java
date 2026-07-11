package com.deepthoughtnet.clinic.api.medicationsafety;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MedicationSafetyEngineTest {
    private final MedicationSafetyEngine engine = new MedicationSafetyEngine();

    @Test
    void detectsExactDuplicateMedicineByMedicineId() {
        UUID tenantId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();
        String medicineId = UUID.randomUUID().toString();
        MedicationSafetyEvaluationResult result = engine.evaluate(request(
                tenantId,
                prescriptionId,
                List.of(
                        item("rx-1", medicineId, "Paracetamol", List.of(), null, "DRAFT", "PENDING_REVIEW"),
                        item("rx-2", medicineId, "Paracetamol", List.of(), null, "DRAFT", "PENDING_REVIEW")
                ),
                List.of(),
                null,
                null
        ));

        assertThat(result.findings()).anySatisfy(finding -> {
            assertThat(finding.ruleCode()).isEqualTo("MED_DUPLICATE_EXACT");
            assertThat(finding.category()).isEqualTo(MedicationSafetyFindingCategory.DUPLICATE_MEDICATION);
            assertThat(finding.severity()).isEqualTo(MedicationSafetySeverity.WARNING);
        });
    }

    @Test
    void doesNotTreatDifferentMedicineIdsAsExactDuplicateWhenOnlyNamesMatch() {
        MedicationSafetyEvaluationResult result = engine.evaluate(request(
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(
                        item("rx-1", UUID.randomUUID().toString(), "Paracetamol", List.of(), null, "DRAFT", "PENDING_REVIEW"),
                        item("rx-2", UUID.randomUUID().toString(), "Paracetamol", List.of(), null, "DRAFT", "PENDING_REVIEW")
                ),
                List.of(),
                null,
                null
        ));

        assertThat(result.findings()).extracting(MedicationSafetyFinding::ruleCode).doesNotContain("MED_DUPLICATE_EXACT");
    }

    @Test
    void differentStrengthProductsWithSameIngredientAreIngredientDuplicatesNotExactDuplicates() {
        MedicationSafetyEvaluationResult result = engine.evaluate(request(
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(
                        item("rx-1", UUID.randomUUID().toString(), "Paracetamol", "500 mg", List.of("Paracetamol"), null, "DRAFT", "PENDING_REVIEW"),
                        item("rx-2", UUID.randomUUID().toString(), "Paracetamol", "650 mg", List.of("Paracetamol"), null, "DRAFT", "PENDING_REVIEW")
                ),
                List.of(),
                new MedicationSafetyEvaluationRequest.AllergySnapshot(null, List.of(), true, false, "UNKNOWN"),
                null
        ));

        assertThat(result.findings()).extracting(MedicationSafetyFinding::ruleCode).contains("MED_DUPLICATE_INGREDIENT");
        assertThat(result.findings()).extracting(MedicationSafetyFinding::ruleCode).doesNotContain("MED_DUPLICATE_EXACT");
        assertThat(result.findings()).anySatisfy(finding -> {
            assertThat(finding.ruleCode()).isEqualTo("MED_DUPLICATE_INGREDIENT");
            assertThat(finding.summary()).contains("Paracetamol");
        });
    }

    @Test
    void noIdsButDistinctProductSignaturesStillPreferIngredientDuplicateOverExactDuplicate() {
        MedicationSafetyEvaluationResult result = engine.evaluate(request(
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(
                        item("rx-1", null, "Paracetamol 650mg Tablet", "650 mg", List.of("Paracetamol"), null, "DRAFT", "PENDING_REVIEW"),
                        item("rx-2", null, "Paracetamol 500 mg Tablet", "500 mg", List.of("Paracetamol"), null, "DRAFT", "PENDING_REVIEW")
                ),
                List.of(),
                new MedicationSafetyEvaluationRequest.AllergySnapshot(null, List.of(), true, false, "UNKNOWN"),
                null
        ));

        assertThat(result.findings()).extracting(MedicationSafetyFinding::ruleCode).contains("MED_DUPLICATE_INGREDIENT");
        assertThat(result.findings()).extracting(MedicationSafetyFinding::ruleCode).doesNotContain("MED_DUPLICATE_EXACT");
    }

    @Test
    void noIdsAndIdenticalProductSignaturesAreExactDuplicates() {
        MedicationSafetyEvaluationResult result = engine.evaluate(request(
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(
                        item("rx-1", null, "Paracetamol 500 mg Tablet", "500 mg", List.of("Paracetamol"), null, "DRAFT", "PENDING_REVIEW"),
                        item("rx-2", null, "Paracetamol 500 mg Tablet", "500 mg", List.of("Paracetamol"), null, "DRAFT", "PENDING_REVIEW")
                ),
                List.of(),
                new MedicationSafetyEvaluationRequest.AllergySnapshot(null, List.of(), true, false, "UNKNOWN"),
                null
        ));

        assertThat(result.findings()).extracting(MedicationSafetyFinding::ruleCode).contains("MED_DUPLICATE_EXACT");
    }

    @Test
    void exactDuplicateMedicineSuppressesIngredientDuplicate() {
        String medicineId = UUID.randomUUID().toString();
        MedicationSafetyEvaluationResult result = engine.evaluate(request(
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(
                        item("rx-1", medicineId, "Paracetamol", "500 mg", List.of("Paracetamol"), null, "DRAFT", "PENDING_REVIEW"),
                        item("rx-2", medicineId, "Paracetamol", "500 mg", List.of("Paracetamol"), null, "DRAFT", "PENDING_REVIEW")
                ),
                List.of(),
                new MedicationSafetyEvaluationRequest.AllergySnapshot(null, List.of(), true, false, "UNKNOWN"),
                null
        ));

        assertThat(result.findings()).extracting(MedicationSafetyFinding::ruleCode).contains("MED_DUPLICATE_EXACT");
        assertThat(result.findings()).extracting(MedicationSafetyFinding::ruleCode).doesNotContain("MED_DUPLICATE_INGREDIENT");
    }

    @Test
    void detectsDuplicateActiveIngredientAcrossDifferentProducts() {
        MedicationSafetyEvaluationResult result = engine.evaluate(request(
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(
                        item("rx-1", UUID.randomUUID().toString(), "Brand A", List.of("Paracetamol"), null, "DRAFT", "PENDING_REVIEW"),
                        item("rx-2", UUID.randomUUID().toString(), "Brand B", List.of("Paracetamol"), null, "DRAFT", "PENDING_REVIEW")
                ),
                List.of(),
                null,
                null
        ));

        assertThat(result.findings()).anySatisfy(finding -> {
            assertThat(finding.ruleCode()).isEqualTo("MED_DUPLICATE_INGREDIENT");
            assertThat(finding.category()).isEqualTo(MedicationSafetyFindingCategory.DUPLICATE_INGREDIENT);
        });
    }

    @Test
    void detectsExactVerifiedAllergyConflict() {
        MedicationSafetyEvaluationResult result = engine.evaluate(request(
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(item("rx-1", UUID.randomUUID().toString(), "Amoxicillin", List.of("Penicillin"), null, "DRAFT", "PENDING_REVIEW")),
                List.of(),
                new MedicationSafetyEvaluationRequest.AllergySnapshot("Penicillin", List.of("Penicillin"), false, false, "VERIFIED"),
                null
        ));

        MedicationSafetyFinding finding = finding(result, "MED_ALLERGY_EXACT");
        assertThat(finding).isNotNull();
        assertThat(finding.category()).isEqualTo(MedicationSafetyFindingCategory.ALLERGY_CONFLICT);
        assertThat(finding.severity()).isEqualTo(MedicationSafetySeverity.CRITICAL);
        assertThat(finding.verificationStatus()).isEqualTo("PENDING_VERIFICATION");
    }

    @Test
    void downgradesPendingAllergyConflictToWarning() {
        MedicationSafetyEvaluationResult result = engine.evaluate(request(
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(item("rx-1", UUID.randomUUID().toString(), "Amoxicillin", List.of("Penicillin"), null, "DRAFT", "PENDING_REVIEW")),
                List.of(),
                new MedicationSafetyEvaluationRequest.AllergySnapshot("Penicillin", List.of("Penicillin"), false, false, "PENDING_VERIFICATION"),
                null
        ));

        MedicationSafetyFinding finding = finding(result, "MED_ALLERGY_EXACT");
        assertThat(finding).isNotNull();
        assertThat(finding.severity()).isEqualTo(MedicationSafetySeverity.WARNING);
        assertThat(finding.verificationStatus()).isEqualTo("PENDING_VERIFICATION");
    }

    @Test
    void flagsUnknownAllergyStatusAsDataQualityWarning() {
        MedicationSafetyEvaluationResult result = engine.evaluate(request(
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(item("rx-1", UUID.randomUUID().toString(), "Amoxicillin", List.of("Penicillin"), null, "DRAFT", "PENDING_REVIEW")),
                List.of(),
                new MedicationSafetyEvaluationRequest.AllergySnapshot(null, List.of(), true, false, "UNKNOWN"),
                null
        ));

        assertThat(result.dataQualityWarnings()).contains("Allergy status is not recorded.");
        MedicationSafetyFinding finding = finding(result, "MED_ALLERGY_STATUS_UNKNOWN");
        assertThat(finding).isNotNull();
        assertThat(finding.category()).isEqualTo(MedicationSafetyFindingCategory.DATA_QUALITY);
    }

    @Test
    void doesNotRaiseDuplicateClassForBroadAnalgesicCategory() {
        MedicationSafetyEvaluationResult result = engine.evaluate(request(
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(
                        item("rx-1", UUID.randomUUID().toString(), "Paracetamol", "500 mg", List.of("Paracetamol"), "Analgesic", "DRAFT", "PENDING_REVIEW"),
                        item("rx-2", UUID.randomUUID().toString(), "Aceclofenac", "100 mg", List.of("Aceclofenac"), "Analgesic", "DRAFT", "PENDING_REVIEW")
                ),
                List.of(),
                new MedicationSafetyEvaluationRequest.AllergySnapshot(null, List.of(), true, false, "UNKNOWN"),
                null
        ));

        assertThat(result.findings()).extracting(MedicationSafetyFinding::ruleCode).doesNotContain("MED_DUPLICATE_CLASS");
        assertThat(result.evaluationCoverage().classDuplicateEvaluated()).isFalse();
        assertThat(result.dataQualityWarnings()).anyMatch(message -> message.contains("Therapeutic class metadata is too broad or unavailable"));
    }

    @Test
    void detectsDuplicateClassOnlyForSpecificAllowlistedClasses() {
        MedicationSafetyEvaluationResult result = engine.evaluate(request(
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(
                        item("rx-1", UUID.randomUUID().toString(), "Diclofenac", "50 mg", List.of("Diclofenac"), "NSAID", "DRAFT", "PENDING_REVIEW"),
                        item("rx-2", UUID.randomUUID().toString(), "Aceclofenac", "100 mg", List.of("Aceclofenac"), "NSAID", "DRAFT", "PENDING_REVIEW")
                ),
                List.of(),
                new MedicationSafetyEvaluationRequest.AllergySnapshot(null, List.of(), true, false, "UNKNOWN"),
                null
        ));

        assertThat(result.findings()).extracting(MedicationSafetyFinding::ruleCode).contains("MED_DUPLICATE_CLASS");
    }

    @Test
    void missingClassMetadataLeavesCoverageUnavailable() {
        MedicationSafetyEvaluationResult result = engine.evaluate(request(
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(
                        item("rx-1", UUID.randomUUID().toString(), "Paracetamol", "500 mg", List.of("Paracetamol"), null, "DRAFT", "PENDING_REVIEW"),
                        item("rx-2", UUID.randomUUID().toString(), "Amlodipine", "5 mg", List.of("Amlodipine"), null, "DRAFT", "PENDING_REVIEW")
                ),
                List.of(),
                new MedicationSafetyEvaluationRequest.AllergySnapshot(null, List.of(), true, false, "UNKNOWN"),
                null
        ));

        assertThat(result.evaluationCoverage().classDuplicateEvaluated()).isFalse();
    }

    @Test
    void evaluatesRenalThresholdConservatively() {
        MedicationSafetyEvaluationResult result = engine.evaluate(request(
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(item("rx-1", UUID.randomUUID().toString(), "Metformin", List.of(), null, "DRAFT", "PENDING_REVIEW")),
                List.of(item("current-1", UUID.randomUUID().toString(), "Amlodipine", List.of(), null, "PRESCRIPTION", "VERIFIED")),
                new MedicationSafetyEvaluationRequest.AllergySnapshot(null, List.of(), true, false, "UNKNOWN"),
                new MedicationSafetyEvaluationRequest.RenalSnapshot("1.8 mg/dL", "2026-05-20", "45 mL/min/1.73m2", "2026-05-20", "VERIFIED", 52, List.of(UUID.randomUUID().toString()))
        ));

        MedicationSafetyFinding finding = finding(result, "MED_RENAL_EGFR_REVIEW");
        assertThat(finding).isNotNull();
        assertThat(finding.category()).isEqualTo(MedicationSafetyFindingCategory.RENAL_CAUTION);
        assertThat(finding.severity()).isEqualTo(MedicationSafetySeverity.WARNING);
        assertThat(result.evaluationCoverage().renalCoverageStatus()).isEqualTo("EVALUATED");
        MedicationSafetyFinding transparency = finding(result, "MED_RENAL_HISTORY_AVAILABLE");
        assertThat(transparency).isNotNull();
        assertThat(transparency.severity()).isEqualTo(MedicationSafetySeverity.INFO);
        assertThat(transparency.summary()).contains("Previous creatinine was 1.8 mg/dL");
    }

    @Test
    void reportsRenalSensitiveMedicineAsUnavailableWhenNoRenalContextExists() {
        MedicationSafetyEvaluationResult result = engine.evaluate(request(
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(item("rx-1", UUID.randomUUID().toString(), "Metformin", List.of(), null, "DRAFT", "PENDING_REVIEW")),
                List.of(),
                new MedicationSafetyEvaluationRequest.AllergySnapshot(null, List.of(), true, false, "UNKNOWN"),
                null
        ));

        assertThat(result.evaluationCoverage().renalEvaluated()).isFalse();
        assertThat(result.evaluationCoverage().renalCoverageStatus()).isEqualTo("UNAVAILABLE");
        MedicationSafetyFinding finding = finding(result, "MED_RENAL_DATA_MISSING");
        assertThat(finding).isNotNull();
        assertThat(finding.category()).isEqualTo(MedicationSafetyFindingCategory.DATA_QUALITY);
        assertThat(finding.severity()).isEqualTo(MedicationSafetySeverity.INFO);
        assertThat(finding(result, "MED_RENAL_HISTORY_AVAILABLE")).isNull();
    }

    @Test
    void historicalRenalContextWithoutRenalSensitiveMedicineIsPartialAndInformational() {
        MedicationSafetyEvaluationResult result = engine.evaluate(request(
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(item("rx-1", UUID.randomUUID().toString(), "Amlodipine", List.of(), null, "DRAFT", "PENDING_REVIEW")),
                List.of(),
                new MedicationSafetyEvaluationRequest.AllergySnapshot(null, List.of(), true, false, "UNKNOWN"),
                new MedicationSafetyEvaluationRequest.RenalSnapshot("1.08 mg/dL", "2026-05-20", "84 mL/min/1.73m2", "2026-05-20", "PENDING_VERIFICATION", 52, List.of("doc-renal-1"))
        ));

        assertThat(result.evaluationCoverage().renalCoverageStatus()).isEqualTo("PARTIAL");
        MedicationSafetyFinding finding = finding(result, "MED_RENAL_HISTORY_AVAILABLE");
        assertThat(finding).isNotNull();
        assertThat(finding.severity()).isEqualTo(MedicationSafetySeverity.INFO);
        assertThat(finding.verificationStatus()).isEqualTo("PENDING_VERIFICATION");
        assertThat(finding.summary()).contains("Previous creatinine was 1.08 mg/dL");
        assertThat(finding.summary()).contains("eGFR was 84 mL/min/1.73m2");
        assertThat(finding.summary()).contains("20-May-2026");
    }

    @Test
    void marksCoverageAndCurrentMedicationOverlap() {
        MedicationSafetyMedicationItem proposed = item("rx-1", UUID.randomUUID().toString(), "Metformin", List.of(), null, "DRAFT", "PENDING_REVIEW");
        MedicationSafetyMedicationItem current = item(null, UUID.randomUUID().toString(), "Metformin", List.of(), null, "LONGITUDINAL_MEMORY", "VERIFIED");
        MedicationSafetyEvaluationResult result = engine.evaluate(request(
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(proposed),
                List.of(current),
                new MedicationSafetyEvaluationRequest.AllergySnapshot(null, List.of(), true, false, "UNKNOWN"),
                null
        ));

        assertThat(result.evaluationCoverage().currentMedicationOverlapEvaluated()).isTrue();
        assertThat(finding(result, "MED_CURRENT_MEDICATION_OVERLAP")).isNotNull();
    }

    @Test
    void noKnownAllergyDoesNotProduceAConflict() {
        MedicationSafetyEvaluationResult result = engine.evaluate(request(
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(item("rx-1", UUID.randomUUID().toString(), "Paracetamol", "500 mg", List.of("Paracetamol"), null, "DRAFT", "PENDING_REVIEW")),
                List.of(),
                new MedicationSafetyEvaluationRequest.AllergySnapshot("No known allergies", List.of(), false, true, "PENDING_REVIEW"),
                null
        ));

        assertThat(result.findings()).extracting(MedicationSafetyFinding::ruleCode).doesNotContain("MED_ALLERGY_EXACT");
        assertThat(result.dataQualityWarnings()).doesNotContain("Allergy status is not recorded.");
    }

    @Test
    void doesNotEmitInteractionFindingForSingleMedicineDraft() {
        MedicationSafetyEvaluationResult result = engine.evaluate(request(
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(item("rx-1", UUID.randomUUID().toString(), "Paracetamol", List.of(), null, "DRAFT", "PENDING_REVIEW")),
                List.of(),
                new MedicationSafetyEvaluationRequest.AllergySnapshot(null, List.of(), true, false, "UNKNOWN"),
                null
        ));

        assertThat(finding(result, "MED_INTERACTION_NOT_EVALUATED")).isNull();
        assertThat(result.dataQualityWarnings()).doesNotContain("Interaction checking is unavailable because no trusted interaction reference is configured.");
    }

    @Test
    void marksInteractionCoverageUnavailableWithoutTrustedReference() {
        MedicationSafetyEvaluationResult result = engine.evaluate(request(
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(
                        item("rx-1", UUID.randomUUID().toString(), "Paracetamol", List.of(), null, "DRAFT", "PENDING_REVIEW"),
                        item("rx-2", UUID.randomUUID().toString(), "Amlodipine", List.of(), null, "DRAFT", "PENDING_REVIEW")
                ),
                List.of(),
                new MedicationSafetyEvaluationRequest.AllergySnapshot(null, List.of(), true, false, "UNKNOWN"),
                null
        ));

        assertThat(finding(result, "MED_INTERACTION_NOT_EVALUATED")).isNull();
        assertThat(result.dataQualityWarnings()).contains("Interaction checking is unavailable because no trusted interaction reference is configured.");
    }

    @Test
    void exactAllergyMatchUsesPersistedIngredientData() {
        MedicationSafetyEvaluationResult result = engine.evaluate(request(
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(item("rx-1", UUID.randomUUID().toString(), "Paracetamol", "500 mg", List.of("Paracetamol"), null, "DRAFT", "PENDING_REVIEW")),
                List.of(),
                new MedicationSafetyEvaluationRequest.AllergySnapshot("Paracetamol", List.of("Paracetamol"), false, false, "PENDING_VERIFICATION"),
                null
        ));

        MedicationSafetyFinding finding = finding(result, "MED_ALLERGY_EXACT");
        assertThat(finding).isNotNull();
        assertThat(finding.summary()).contains("Paracetamol");
    }

    @Test
    void overallSeverityReflectsHighestFindingSeverity() {
        MedicationSafetyEvaluationResult result = engine.evaluate(request(
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(
                        item("rx-1", UUID.randomUUID().toString(), "Amoxicillin", List.of("Penicillin"), null, "DRAFT", "PENDING_REVIEW"),
                        item("rx-2", UUID.randomUUID().toString(), "Amoxicillin", List.of("Penicillin"), null, "DRAFT", "PENDING_REVIEW")
                ),
                List.of(),
                new MedicationSafetyEvaluationRequest.AllergySnapshot("Penicillin", List.of("Penicillin"), false, false, "VERIFIED"),
                new MedicationSafetyEvaluationRequest.RenalSnapshot("1.8 mg/dL", "2026-05-20", "45 mL/min/1.73m2", "2026-05-20", "VERIFIED", 52, List.of(UUID.randomUUID().toString()))
        ));

        assertThat(result.overallSeverity()).isEqualTo(MedicationSafetySeverity.CRITICAL);
    }

    private MedicationSafetyFinding finding(MedicationSafetyEvaluationResult result, String ruleCode) {
        return result.findings().stream().filter(finding -> ruleCode.equals(finding.ruleCode())).findFirst().orElse(null);
    }

    private MedicationSafetyEvaluationRequest request(UUID tenantId,
                                                       UUID prescriptionId,
                                                       List<MedicationSafetyMedicationItem> proposed,
                                                       List<MedicationSafetyMedicationItem> current,
                                                       MedicationSafetyEvaluationRequest.AllergySnapshot allergies,
                                                       MedicationSafetyEvaluationRequest.RenalSnapshot renalContext) {
        return new MedicationSafetyEvaluationRequest(
                tenantId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                prescriptionId,
                "DRAFT",
                proposed,
                current,
                allergies,
                List.of("Diabetes Mellitus"),
                renalContext,
                null,
                42,
                "MALE",
                null,
                Map.of()
        );
    }

    private MedicationSafetyMedicationItem item(String prescriptionItemId,
                                                String medicineId,
                                                String medicineName,
                                                List<String> activeIngredients,
                                                String therapeuticClass,
                                                String source,
                                                String verificationStatus) {
        return item(prescriptionItemId, medicineId, medicineName, null, activeIngredients, therapeuticClass, source, verificationStatus);
    }

    private MedicationSafetyMedicationItem item(String prescriptionItemId,
                                                String medicineId,
                                                String medicineName,
                                                String strength,
                                                List<String> activeIngredients,
                                                String therapeuticClass,
                                                String source,
                                                String verificationStatus) {
        return new MedicationSafetyMedicationItem(
                prescriptionItemId,
                medicineId,
                medicineName,
                normalize(medicineName),
                activeIngredients,
                therapeuticClass,
                strength,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                source,
                "ACTIVE",
                verificationStatus,
                BigDecimal.valueOf(0.9),
                UUID.randomUUID().toString(),
                medicineName,
                LocalDate.of(2026, 7, 11)
        );
    }

    private String normalize(String value) {
        return value == null ? null : value.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    }
}
