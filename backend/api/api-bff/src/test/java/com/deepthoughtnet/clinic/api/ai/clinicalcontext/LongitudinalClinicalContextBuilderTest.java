package com.deepthoughtnet.clinic.api.ai.clinicalcontext;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentEntity;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import com.deepthoughtnet.clinic.api.clinicalmemory.model.LongitudinalConceptSnapshot;
import com.deepthoughtnet.clinic.api.clinicalmemory.model.PatientLongitudinalMemoryProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
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
                        new LongitudinalConceptSnapshot("LAB_RESULT", "hba1c", "HbA1c", "7.3", "%", "HbA1c report", "EXTERNAL_LAB_REPORT", documentId, LocalDate.of(2026, 1, 15), BigDecimal.valueOf(0.95), "ACCEPTED", "HbA1c 7.3 %"),
                        new LongitudinalConceptSnapshot("LAB_RESULT", "hba1c", "HbA1c", "8.4", "%", "HbA1c report", "EXTERNAL_LAB_REPORT", documentId, LocalDate.of(2026, 7, 10), BigDecimal.valueOf(0.95), "ACCEPTED", "HbA1c 8.4 %"),
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

        assertThat(context.imagingHistory()).hasSize(1);
        assertThat(context.imagingHistory().getFirst().summary()).contains("bronchitic").contains("No evidence of pneumonia");
        assertThat(context.imagingHistory().getFirst().verificationStatus()).isEqualTo("PENDING_VERIFICATION");

        assertThat(context.renalContext()).isNotNull();
        assertThat(context.renalContext().interpretation()).contains("preserved");

        assertThat(context.importantHistoricalFindings()).extracting(ClinicalContextResponse.HistoricalFinding::title)
                .containsExactly("Worsening glycemic control", "Previous chest imaging", "Previous renal function");
        assertThat(context.dataQualityWarnings()).isEmpty();
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
        assertThat(context.dataQualityWarnings()).contains("HbA1c trend could not be confirmed because only one reliable report date was available.");
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
