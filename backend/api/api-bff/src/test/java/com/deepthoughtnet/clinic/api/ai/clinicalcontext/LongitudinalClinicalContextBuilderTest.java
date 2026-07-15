package com.deepthoughtnet.clinic.api.ai.clinicalcontext;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentEntity;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import com.deepthoughtnet.clinic.api.clinicalmemory.model.LongitudinalConceptSnapshot;
import com.deepthoughtnet.clinic.api.clinicalmemory.model.PatientLongitudinalMemoryProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LongitudinalClinicalContextBuilderTest {

    private final LongitudinalClinicalContextBuilder builder = new LongitudinalClinicalContextBuilder(new ObjectMapper());

    @Test
    void buildsHbA1cTrendImagingHistoryAndRenalContextFromReliableHistory() {
        UUID documentId = UUID.randomUUID();
        PatientLongitudinalMemoryProfile profile = new PatientLongitudinalMemoryProfile(
                List.of(new LongitudinalConceptSnapshot("CONDITION", "diabetes_mellitus", "Diabetes Mellitus", "Diabetes Mellitus", null, "Follow-up", "EXTERNAL_LAB_REPORT", documentId, LocalDate.of(2026, 1, 15), BigDecimal.valueOf(0.95), "ACCEPTED", "Known diabetic")),
                List.of(),
                null,
                null,
                List.of(),
                null,
                null,
                List.of(),
                List.of(
                        new LongitudinalConceptSnapshot("LAB_RESULT", "hba1c", "HbA1c", "7.3", "%", "HbA1c report", "EXTERNAL_LAB_REPORT", documentId, LocalDate.of(2026, 1, 15), BigDecimal.valueOf(0.95), "VERIFIED", "HbA1c 7.3 %"),
                        new LongitudinalConceptSnapshot("LAB_RESULT", "hba1c", "HbA1c", "8.4", "%", "HbA1c report", "EXTERNAL_LAB_REPORT", documentId, LocalDate.of(2026, 7, 8), BigDecimal.valueOf(0.95), "PENDING_REVIEW", "HbA1c 8.4 %"),
                        new LongitudinalConceptSnapshot("LAB_RESULT", "creatinine", "Creatinine", "1.08", "mg/dL", "Kidney function", "EXTERNAL_LAB_REPORT", documentId, LocalDate.of(2026, 5, 20), BigDecimal.valueOf(0.95), "ACCEPTED", "Creatinine 1.08 mg/dL"),
                        new LongitudinalConceptSnapshot("LAB_RESULT", "egfr", "eGFR", "84", "mL/min/1.73m2", "Kidney function", "EXTERNAL_LAB_REPORT", documentId, LocalDate.of(2026, 5, 20), BigDecimal.valueOf(0.95), "ACCEPTED", "eGFR 84 mL/min/1.73m2")
                ),
                "HbA1c 8.4"
        );
        ClinicalDocumentEntity chestXray = document("05_Chest_Xray_Report", ClinicalDocumentType.X_RAY, LocalDate.of(2026, 7, 2),
                "{\"summary\":\"Mild bronchitic changes without focal consolidation. No pleural effusion. No pneumothorax. No evidence of pneumonia.\"}",
                "PENDING_REVIEW");

        ClinicalContextResponse.LongitudinalClinicalContext context = builder.build(profile, List.of(chestXray));

        assertThat(context.labTrends()).hasSize(1);
        ClinicalContextResponse.LabTrend hba1cTrend = context.labTrends().getFirst();
        assertThat(hba1cTrend.analyteCode()).isEqualTo("hba1c");
        assertThat(hba1cTrend.direction()).isEqualTo("WORSENING");
        assertThat(hba1cTrend.absoluteChange()).isEqualTo("+1.1 percentage points");
        assertThat(hba1cTrend.verificationStatus()).isEqualTo("PENDING_VERIFICATION");

        assertThat(context.imagingHistory()).hasSize(1);
        assertThat(context.imagingHistory().getFirst().summary()).contains("bronchitic").contains("No evidence of pneumonia");
        assertThat(context.imagingHistory().getFirst().verificationStatus()).isEqualTo("PENDING_VERIFICATION");

        assertThat(context.renalContext()).isNotNull();
        assertThat(context.renalContext().interpretation()).contains("preserved");

        assertThat(context.importantHistoricalFindings()).extracting(ClinicalContextResponse.HistoricalFinding::title)
                .containsExactly("Worsening glycemic control", "Previous chest imaging", "Previous renal function");
        assertThat(context.importantHistoricalFindings()).filteredOn(finding -> "LAB_TREND".equals(finding.kind()))
                .first()
                .satisfies(finding -> {
                    assertThat(finding.summary()).contains("7.3% on 15-Jan-2026", "8.4% on 08-Jul-2026", "+1.1 percentage points");
                    assertThat(finding.clinicalRelevance()).contains("approximately 6 months", "susceptibility to infection or delay recovery");
                });
        assertThat(context.dataQualityWarnings()).isEmpty();
    }

    @Test
    void selectsLatestHbA1cVersusImmediatelyPreviousDistinctObservation() {
        UUID documentId = UUID.randomUUID();
        PatientLongitudinalMemoryProfile profile = new PatientLongitudinalMemoryProfile(
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(
                        new LongitudinalConceptSnapshot("LAB_RESULT", "hba1c", "HbA1c", "8.4", "%", "HbA1c follow-up report", "EXTERNAL_LAB_REPORT", documentId, LocalDate.of(2026, 1, 8), BigDecimal.valueOf(0.95), "VERIFIED", "HbA1c 8.4 %"),
                        new LongitudinalConceptSnapshot("LAB_RESULT", "hba1c", "HbA1c", "7.3", "%", "HbA1c follow-up report", "EXTERNAL_LAB_REPORT", documentId, LocalDate.of(2026, 1, 15), BigDecimal.valueOf(0.95), "VERIFIED", "HbA1c 7.3 %"),
                        new LongitudinalConceptSnapshot("LAB_RESULT", "hba1c", "HbA1c", "8.4", "%", "HbA1c follow-up report", "EXTERNAL_LAB_REPORT", documentId, LocalDate.of(2026, 7, 8), BigDecimal.valueOf(0.95), "PENDING_REVIEW", "HbA1c 8.4 %")
                ),
                null
        );

        ClinicalContextResponse.LongitudinalClinicalContext context = builder.build(profile, List.of());

        assertThat(context.labTrends()).hasSize(1);
        assertThat(context.labTrends().getFirst().olderDate()).isEqualTo("2026-01-15");
        assertThat(context.labTrends().getFirst().newerDate()).isEqualTo("2026-07-08");
        assertThat(context.labTrends().getFirst().direction()).isEqualTo("WORSENING");
        assertThat(context.labTrends().getFirst().absoluteChange()).isEqualTo("+1.1 percentage points");
        assertThat(context.importantHistoricalFindings()).first()
                .satisfies(finding -> {
                    assertThat(finding.title()).isEqualTo("Worsening glycemic control");
                    assertThat(finding.summary()).contains("7.3% on 15-Jan-2026", "8.4% on 08-Jul-2026");
                });
    }

    @Test
    void deduplicatesReprocessedHbA1cCopiesBeforeTrendSelection() {
        UUID documentId = UUID.randomUUID();
        UUID retestDocumentId = UUID.randomUUID();
        PatientLongitudinalMemoryProfile profile = new PatientLongitudinalMemoryProfile(
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(
                        new LongitudinalConceptSnapshot("LAB_RESULT", "hba1c", "HbA1c", "8.4", "%", "HbA1c report", "EXTERNAL_LAB_REPORT", documentId, LocalDate.of(2026, 1, 8), BigDecimal.valueOf(0.96), "VERIFIED", "HbA1c 8.4 %"),
                        new LongitudinalConceptSnapshot("LAB_RESULT", "hba1c", "HbA1c", "8.4", "%", "HbA1c report retest", "EXTERNAL_LAB_REPORT", retestDocumentId, LocalDate.of(2026, 1, 8), BigDecimal.valueOf(0.80), "PENDING_REVIEW", "HbA1c 8.4 %"),
                        new LongitudinalConceptSnapshot("LAB_RESULT", "hba1c", "HbA1c", "7.3", "%", "HbA1c follow-up report", "EXTERNAL_LAB_REPORT", documentId, LocalDate.of(2026, 1, 15), BigDecimal.valueOf(0.95), "VERIFIED", "HbA1c 7.3 %"),
                        new LongitudinalConceptSnapshot("LAB_RESULT", "hba1c", "HbA1c", "8.4", "%", "HbA1c follow-up report", "EXTERNAL_LAB_REPORT", documentId, LocalDate.of(2026, 7, 8), BigDecimal.valueOf(0.95), "PENDING_REVIEW", "HbA1c 8.4 %"),
                        new LongitudinalConceptSnapshot("LAB_RESULT", "hba1c", "HbA1c", "8.4", "%", "HbA1c follow-up report retest", "EXTERNAL_LAB_REPORT", retestDocumentId, LocalDate.of(2026, 7, 8), BigDecimal.valueOf(0.70), "PENDING_REVIEW", "HbA1c 8.4 %")
                ),
                null
        );

        ClinicalContextResponse.LongitudinalClinicalContext context = builder.build(profile, List.of());

        assertThat(context.labTrends()).hasSize(1);
        assertThat(context.labTrends().getFirst().olderDate()).isEqualTo("2026-01-15");
        assertThat(context.labTrends().getFirst().newerDate()).isEqualTo("2026-07-08");
        assertThat(context.labTrends().getFirst().direction()).isEqualTo("WORSENING");
        assertThat(context.labTrends().getFirst().verificationStatus()).isEqualTo("PENDING_VERIFICATION");
    }

    @Test
    void keepsEstimatedAverageGlucoseSeparateFromBloodSugarTrendSelection() {
        UUID bloodSugarOlderDocumentId = UUID.randomUUID();
        UUID estimatedAverageGlucoseDocumentId = UUID.randomUUID();
        UUID bloodSugarNewerDocumentId = UUID.randomUUID();
        PatientLongitudinalMemoryProfile profile = new PatientLongitudinalMemoryProfile(
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(
                        new LongitudinalConceptSnapshot("LAB_RESULT", "blood_sugar", "Random Blood Sugar", "140", "mg/dL", "Baseline glucose", "EXTERNAL_LAB_REPORT", bloodSugarOlderDocumentId, LocalDate.of(2026, 1, 1), BigDecimal.valueOf(0.95), "VERIFIED", "Random Blood Sugar 140 mg/dL"),
                        new LongitudinalConceptSnapshot("LAB_RESULT", "estimated_average_glucose", "Estimated Average Glucose", "163", "mg/dL", "Estimated Average Glucose report", "EXTERNAL_LAB_REPORT", estimatedAverageGlucoseDocumentId, LocalDate.of(2026, 1, 15), BigDecimal.valueOf(0.95), "PENDING_REVIEW", "Estimated Average Glucose 163 mg/dL"),
                        new LongitudinalConceptSnapshot("LAB_RESULT", "blood_sugar", "Blood Glucose", "198", "mg/dL", "Follow-up glucose", "EXTERNAL_LAB_REPORT", bloodSugarNewerDocumentId, LocalDate.of(2026, 7, 8), BigDecimal.valueOf(0.95), "PENDING_REVIEW", "Blood Glucose 198 mg/dL")
                ),
                null
        );

        ClinicalContextResponse.LongitudinalClinicalContext context = builder.build(profile, List.of());

        assertThat(context.labTrends()).hasSize(1);
        assertThat(context.labTrends().getFirst().analyteCode()).isEqualTo("blood_sugar");
        assertThat(context.labTrends().getFirst().olderValue()).isEqualTo("140");
        assertThat(context.labTrends().getFirst().olderDate()).isEqualTo("2026-01-01");
        assertThat(context.labTrends().getFirst().newerValue()).isEqualTo("198");
        assertThat(context.labTrends().getFirst().newerDate()).isEqualTo("2026-07-08");
        assertThat(context.labTrends().getFirst().direction()).isEqualTo("WORSENING");
    }

    @Test
    void normalizesEstimatedAverageGlucoseAliasesWithoutCollidingWithBloodSugarAliases() throws Exception {
        assertThat(invokeNormalizeAnalyteKey("estimated_average_glucose", null, null)).isEqualTo("estimated_average_glucose");
        assertThat(invokeNormalizeAnalyteKey(null, "Estimated Average Glucose", null)).isEqualTo("estimated_average_glucose");
        assertThat(invokeNormalizeAnalyteKey(null, "Estimated Avg Glucose", null)).isEqualTo("estimated_average_glucose");
        assertThat(invokeNormalizeAnalyteKey(null, "eAG", null)).isEqualTo("estimated_average_glucose");
        assertThat(invokeNormalizeAnalyteKey(null, "Random Blood Sugar", null)).isEqualTo("blood_sugar");
        assertThat(invokeNormalizeAnalyteKey(null, "Blood Glucose", null)).isEqualTo("blood_sugar");
    }

    @Test
    void preservesValidBloodSugarAliasTrendSelection() {
        UUID firstDocumentId = UUID.randomUUID();
        UUID secondDocumentId = UUID.randomUUID();
        PatientLongitudinalMemoryProfile profile = new PatientLongitudinalMemoryProfile(
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(
                        new LongitudinalConceptSnapshot("LAB_RESULT", "blood_sugar", "Random Blood Sugar", "140", "mg/dL", "Baseline glucose", "EXTERNAL_LAB_REPORT", firstDocumentId, LocalDate.of(2026, 1, 1), BigDecimal.valueOf(0.95), "VERIFIED", "Random Blood Sugar 140 mg/dL"),
                        new LongitudinalConceptSnapshot("LAB_RESULT", "blood_sugar", "Blood Glucose", "198", "mg/dL", "Follow-up glucose", "EXTERNAL_LAB_REPORT", secondDocumentId, LocalDate.of(2026, 7, 8), BigDecimal.valueOf(0.95), "PENDING_REVIEW", "Blood Glucose 198 mg/dL")
                ),
                null
        );

        ClinicalContextResponse.LongitudinalClinicalContext context = builder.build(profile, List.of());

        assertThat(context.labTrends()).hasSize(1);
        assertThat(context.labTrends().getFirst().analyteCode()).isEqualTo("blood_sugar");
        assertThat(context.labTrends().getFirst().olderValue()).isEqualTo("140");
        assertThat(context.labTrends().getFirst().newerValue()).isEqualTo("198");
        assertThat(context.labTrends().getFirst().direction()).isEqualTo("WORSENING");
    }

    @Test
    void preservesGenericAnalyteTrendSelectionForEgfr() {
        UUID documentId = UUID.randomUUID();
        PatientLongitudinalMemoryProfile profile = new PatientLongitudinalMemoryProfile(
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(
                        new LongitudinalConceptSnapshot("LAB_RESULT", "egfr", "eGFR", "95", "mL/min/1.73m2", "Kidney function report", "EXTERNAL_LAB_REPORT", documentId, LocalDate.of(2026, 1, 8), BigDecimal.valueOf(0.95), "VERIFIED", "eGFR 95 mL/min/1.73m2"),
                        new LongitudinalConceptSnapshot("LAB_RESULT", "egfr", "eGFR", "90", "mL/min/1.73m2", "Kidney function report", "EXTERNAL_LAB_REPORT", documentId, LocalDate.of(2026, 1, 15), BigDecimal.valueOf(0.95), "VERIFIED", "eGFR 90 mL/min/1.73m2"),
                        new LongitudinalConceptSnapshot("LAB_RESULT", "egfr", "eGFR", "84", "mL/min/1.73m2", "Kidney function report", "EXTERNAL_LAB_REPORT", documentId, LocalDate.of(2026, 7, 8), BigDecimal.valueOf(0.95), "PENDING_REVIEW", "eGFR 84 mL/min/1.73m2")
                ),
                null
        );

        ClinicalContextResponse.LongitudinalClinicalContext context = builder.build(profile, List.of());

        assertThat(context.labTrends()).extracting(ClinicalContextResponse.LabTrend::analyteCode).contains("egfr");
        assertThat(context.labTrends()).filteredOn(trend -> "egfr".equals(trend.analyteCode()))
                .first()
                .satisfies(trend -> {
                    assertThat(trend.olderDate()).isEqualTo("2026-01-15");
                    assertThat(trend.newerDate()).isEqualTo("2026-07-08");
                    assertThat(trend.direction()).isEqualTo("WORSENING");
                });
    }

    private String invokeNormalizeAnalyteKey(String conceptKey, String label, String evidenceText) throws Exception {
        Method method = LongitudinalClinicalContextBuilder.class.getDeclaredMethod("normalizeAnalyteKey", String.class, String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(builder, conceptKey, label, evidenceText);
    }

    @Test
    void excludesMockLabReportsFromImagingAndFallsBackToMetadataOnlyForChestXrayWithoutTrustedFindings() {
        UUID documentId = UUID.randomUUID();
        PatientLongitudinalMemoryProfile profile = new PatientLongitudinalMemoryProfile(
                List.of(),
                List.of(),
                null,
                null,
                List.of(),
                null,
                null,
                List.of(),
                List.of(),
                "Mock AI provider is active. No external model was called."
        );

        ClinicalDocumentEntity labReport = document("Rohan_Sharma_HbA1c_Followup_Report_2026-07-08", ClinicalDocumentType.EXTERNAL_LAB_REPORT, LocalDate.of(2026, 7, 8),
                null,
                "PENDING_REVIEW");
        setField(labReport, "aiExtractionSummary", "Mock AI provider is active. No external model was called.");

        ClinicalDocumentEntity chestXray = document("05_Chest_Xray_Report", ClinicalDocumentType.EXTERNAL_LAB_REPORT, LocalDate.of(2026, 7, 2),
                null,
                "PENDING_REVIEW");
        setField(chestXray, "aiExtractionSummary", "Mock AI provider is active. No external model was called.");

        ClinicalContextResponse.LongitudinalClinicalContext context = builder.build(profile, List.of(labReport, chestXray));

        assertThat(context.imagingHistory()).hasSize(1);
        assertThat(context.imagingHistory().getFirst().summary())
                .contains("structured radiology findings are not currently available")
                .doesNotContain("Mock AI provider is active");
        assertThat(context.importantHistoricalFindings()).extracting(ClinicalContextResponse.HistoricalFinding::title)
                .contains("Previous chest imaging");
        assertThat(context.importantHistoricalFindings()).filteredOn(finding -> "Previous chest imaging".equals(finding.title()))
                .first()
                .satisfies(finding -> assertThat(finding.summary())
                        .contains("structured radiology findings are not currently available")
                        .doesNotContain("Mock AI provider is active"));
    }

    @Test
    void buildsRenalContextFromPendingReviewKidneyFunctionMemory() {
        UUID documentId = UUID.randomUUID();
        PatientLongitudinalMemoryProfile profile = new PatientLongitudinalMemoryProfile(
                List.of(),
                List.of(),
                null,
                null,
                List.of(),
                null,
                null,
                List.of(),
                List.of(
                        new LongitudinalConceptSnapshot("LAB_RESULT", "creatinine", "Creatinine", "1.08", "mg/dL", "Kidney Function Report", "EXTERNAL_LAB_REPORT", documentId, LocalDate.of(2026, 5, 20), BigDecimal.valueOf(0.18), "PENDING_REVIEW", "Creatinine 1.08 mg/dL"),
                        new LongitudinalConceptSnapshot("LAB_RESULT", "egfr", "eGFR", "84", "mL/min/1.73m2", "Kidney Function Report", "EXTERNAL_LAB_REPORT", documentId, LocalDate.of(2026, 5, 20), BigDecimal.valueOf(0.18), "PENDING_REVIEW", "eGFR 84 mL/min/1.73m2")
                ),
                "Kidney Function Report"
        );
        ClinicalDocumentEntity kidneyDocument = document("03_Kidney_Function_Report", ClinicalDocumentType.EXTERNAL_LAB_REPORT, LocalDate.of(2026, 5, 20),
                "{\"factualFindings\":{\"labResults\":[{\"canonicalKey\":\"creatinine\",\"testName\":\"Creatinine\",\"value\":\"1.08\",\"unit\":\"mg/dL\"},{\"canonicalKey\":\"egfr\",\"testName\":\"eGFR\",\"value\":\"84\",\"unit\":\"mL/min/1.73m²\"}]}}",
                "PENDING_REVIEW");

        ClinicalContextResponse.LongitudinalClinicalContext context = builder.build(profile, List.of(kidneyDocument));

        assertThat(context.renalContext()).isNotNull();
        assertThat(context.renalContext().creatinine()).isEqualTo("1.08 mg/dL");
        assertThat(context.renalContext().egfr()).isEqualTo("84 mL/min/1.73m2");
        assertThat(context.renalContext().verificationStatus()).isEqualTo("PENDING_VERIFICATION");
        assertThat(context.importantHistoricalFindings()).extracting(ClinicalContextResponse.HistoricalFinding::title)
                .contains("Previous renal function");
        assertThat(context.importantHistoricalFindings()).extracting(ClinicalContextResponse.HistoricalFinding::summary)
                .anyMatch(summary -> summary != null
                        && summary.contains("Creatinine 1.08 mg/dL")
                        && summary.contains("eGFR 84 mL/min/1.73m2"));
        assertThat(context.dataQualityWarnings()).isEmpty();
    }

    @Test
    void preservesOneDecimalAndIntegerRenalSourceValues() {
        UUID documentId = UUID.randomUUID();
        PatientLongitudinalMemoryProfile profile = new PatientLongitudinalMemoryProfile(
                List.of(),
                List.of(),
                null,
                null,
                List.of(),
                null,
                null,
                List.of(),
                List.of(
                        new LongitudinalConceptSnapshot("LAB_RESULT", "creatinine", "Creatinine", "1.1", "mg/dL", "Kidney Function Report", "EXTERNAL_LAB_REPORT", documentId, LocalDate.of(2026, 5, 20), BigDecimal.valueOf(0.18), "PENDING_REVIEW", "Creatinine 1.1 mg/dL"),
                        new LongitudinalConceptSnapshot("LAB_RESULT", "egfr", "eGFR", "84", "mL/min/1.73m2", "Kidney Function Report", "EXTERNAL_LAB_REPORT", documentId, LocalDate.of(2026, 5, 20), BigDecimal.valueOf(0.18), "PENDING_REVIEW", "eGFR 84 mL/min/1.73m2")
                ),
                "Kidney Function Report"
        );

        ClinicalContextResponse.LongitudinalClinicalContext context = builder.build(profile, List.of());

        assertThat(context.renalContext()).isNotNull();
        assertThat(context.renalContext().creatinine()).isEqualTo("1.1 mg/dL");
        assertThat(context.renalContext().egfr()).isEqualTo("84 mL/min/1.73m2");
    }

    @Test
    void suppressesTrendWhenHbA1cHasOnlyOneReliableDateAndDoesNotFallbackToGenericConditions() {
        UUID documentId = UUID.randomUUID();
        PatientLongitudinalMemoryProfile profile = new PatientLongitudinalMemoryProfile(
                List.of(new LongitudinalConceptSnapshot("CONDITION", "diabetes_mellitus", "Diabetes Mellitus", "Diabetes Mellitus", null, "Follow-up", "EXTERNAL_LAB_REPORT", documentId, LocalDate.of(2026, 1, 15), BigDecimal.valueOf(0.95), "ACCEPTED", "Known diabetic")),
                List.of(),
                null,
                null,
                List.of(),
                null,
                null,
                List.of(new LongitudinalConceptSnapshot("RISK_FLAG", "lipid_risk", "Dyslipidemia", "Dyslipidemia", null, "Follow-up", "EXTERNAL_LAB_REPORT", documentId, LocalDate.of(2026, 1, 15), BigDecimal.valueOf(0.95), "ACCEPTED", "Lipid risk")),
                List.of(new LongitudinalConceptSnapshot("LAB_RESULT", "hba1c", "HbA1c", "7.3", "%", "HbA1c report", "EXTERNAL_LAB_REPORT", documentId, LocalDate.of(2026, 1, 15), BigDecimal.valueOf(0.95), "ACCEPTED", "HbA1c 7.3 %")),
                "HbA1c 7.3"
        );

        ClinicalContextResponse.LongitudinalClinicalContext context = builder.build(profile, List.of());

        assertThat(context.labTrends()).isEmpty();
        assertThat(context.importantHistoricalFindings()).isEmpty();
        assertThat(context.dataQualityWarnings()).contains("HbA1c trend could not be confirmed because only one report date was available.");
    }

    @Test
    void consolidatesDuplicateReprocessedObservationsAndWarnsWhenRenalValuesAreMissing() {
        UUID sourceDocumentId = UUID.randomUUID();
        PatientLongitudinalMemoryProfile profile = new PatientLongitudinalMemoryProfile(
                List.of(),
                List.of(),
                null,
                null,
                List.of(),
                null,
                null,
                List.of(),
                List.of(
                        new LongitudinalConceptSnapshot("LAB_RESULT", "hba1c", "HbA1c", "7.3", "%", "Retest 1", "EXTERNAL_LAB_REPORT", sourceDocumentId, LocalDate.of(2026, 1, 15), BigDecimal.valueOf(0.80), "PENDING_REVIEW", "HbA1c 7.3 %"),
                        new LongitudinalConceptSnapshot("LAB_RESULT", "hba1c", "HbA1c", "7.3", "%", "Original report", "EXTERNAL_LAB_REPORT", sourceDocumentId, LocalDate.of(2026, 1, 15), BigDecimal.valueOf(0.95), "ACCEPTED", "HbA1c 7.3 %")
                ),
                null
        );
        ClinicalDocumentEntity renalDocument = document("Kidney Function Report", ClinicalDocumentType.EXTERNAL_LAB_REPORT, LocalDate.of(2026, 5, 20), null, "PENDING_REVIEW");
        setField(renalDocument, "description", "Kidney function follow-up");

        ClinicalContextResponse.LongitudinalClinicalContext context = builder.build(profile, List.of(renalDocument));

        assertThat(context.dataQualityWarnings()).contains("Multiple duplicate reprocessed observations were consolidated.");
        assertThat(context.dataQualityWarnings()).contains("Kidney-function report exists but creatinine/eGFR values were not extracted.");
    }

    private static ClinicalDocumentEntity document(String title,
                                                   ClinicalDocumentType type,
                                                   LocalDate reportDate,
                                                   String acceptedJson,
                                                   String verificationStatus) {
        ClinicalDocumentEntity entity = ClinicalDocumentEntity.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                type,
                title,
                "application/pdf",
                1024,
                "checksum",
                "patients/1/documents/report.pdf",
                title,
                null,
                null,
                null
        );
        setField(entity, "reportDate", reportDate);
        setField(entity, "aiExtractionAcceptedJson", acceptedJson);
        setField(entity, "verificationStatus", verificationStatus);
        return entity;
    }

    private static void setField(Object entity, String fieldName, Object value) {
        try {
            Field field = entity.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(entity, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to set field " + fieldName, ex);
        }
    }
}
