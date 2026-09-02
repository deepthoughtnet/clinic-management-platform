package com.deepthoughtnet.clinic.api.clinicaldocument.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.ai.dto.AiDraftResponse;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.model.ClinicalDocumentExtraction;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClinicalDocumentExtractionResponseAdapterTest {
    private final ClinicalDocumentExtractionResponseAdapter adapter = new ClinicalDocumentExtractionResponseAdapter();

    @Test
    void equivalentProviderLabLocationsProduceTheSameCanonicalRows() {
        List<String> paths = List.of(
                "labResults",
                "factualFindings.labResults",
                "answer.labResults",
                "answer.extractedData.labResults",
                "answer.extractedClinicalData.labResults",
                "answer.clinicalDocumentExtraction.labResults",
                "extractedData.labResults",
                "data.labResults",
                "answer.data.labResults",
                "answer.payload.clinical.labResults",
                "extractedClinicalData.labResults"
        );

        List<ClinicalDocumentExtraction.LabResult> expected = adapter.adapt(
                Map.of("labResults", rows()), response()).labResults();

        for (String path : paths) {
            Map<String, Object> payload = new LinkedHashMap<>();
            putAtPath(payload, path, rows());
            assertThat(adapter.adapt(payload, response()).labResults())
                    .as("canonical rows from %s", path)
                    .containsExactlyElementsOf(expected);
        }
    }

    @Test
    void providerFieldAliasesAndResultValueVariantsAreCanonicalized() {
        Map<String, Object> payload = Map.of("answer", Map.of("extractedClinicalData", Map.of(
                "labResults", List.of(
                        Map.of("test", "HBA1C", "value", "5.7", "unit", "%"),
                        Map.of("label", "Hemoglobin A1c", "result", "5.8"),
                        Map.of("name", "Glycated Hemoglobin", "result", "5.9"),
                        Map.of("analyte", "Glycosylated Hemoglobin", "result", "6.0"),
                        Map.of("parameter", "Fasting Glucose", "result", "102")
                )
        )));

        assertThat(adapter.adapt(payload, response()).labResults())
                .extracting(ClinicalDocumentExtraction.LabResult::testName)
                .containsExactly("HBA1C", "Hemoglobin A1c", "Glycated Hemoglobin", "Glycosylated Hemoglobin", "Fasting Glucose");
        assertThat(adapter.adapt(payload, response()).labResults().get(0).value()).isEqualTo("5.7");
    }

    @Test
    void structuredRowsDisableNarrativeFallbackAndJsonIsNotNarrative() {
        Map<String, Object> payload = Map.of(
                "answer", Map.of("labResults", List.of(Map.of("testName", "HbA1c", "result", "5.7"))),
                "rawText", "{\"labResults\":[{\"testName\":\"HbA1c\"}]}"
        );

        ClinicalDocumentExtraction extraction = adapter.adapt(payload, response());

        assertThat(extraction.labResults()).hasSize(1);
        assertThat(extraction.narrativeTexts()).isEmpty();
    }

    @Test
    void oneLevelJsonStringWrapperIsResolvedWithoutNarrativeParsing() {
        Map<String, Object> payload = Map.of(
                "answer", "{\"labResults\":[{\"testName\":\"HbA1c\",\"result\":\"5.7\",\"unit\":\"%\"}]}"
        );

        ClinicalDocumentExtraction extraction = adapter.adapt(payload, response());

        assertThat(extraction.labResults()).hasSize(1);
        assertThat(extraction.labResults().get(0).testName()).isEqualTo("HbA1c");
        assertThat(extraction.labResults().get(0).value()).isEqualTo("5.7");
        assertThat(extraction.narrativeTexts()).isEmpty();
    }

    @Test
    void unrelatedLabResultsArrayIsRejectedAndEmptyArrayDoesNotSuppressNarrative() {
        ClinicalDocumentExtraction invalid = adapter.adapt(
                Map.of("labResults", List.of(Map.of("message", "not a lab fact")), "summary", "HbA1c is 5.7 percent."), null);
        ClinicalDocumentExtraction empty = adapter.adapt(
                Map.of("labResults", List.of(), "summary", "HbA1c is 5.7 percent."), null);

        assertThat(invalid.labResults()).isEmpty();
        assertThat(invalid.narrativeTexts()).containsExactly("HbA1c is 5.7 percent.");
        assertThat(empty.labResults()).isEmpty();
        assertThat(empty.narrativeTexts()).containsExactly("HbA1c is 5.7 percent.");
    }

    @Test
    void largestStructurallyValidCandidateWinsAndConflictsAreMarked() {
        Map<String, Object> payload = Map.of(
                "labResults", List.of(Map.of("testName", "HbA1c", "result", "5.7")),
                "answer", Map.of("labResults", List.of(
                        Map.of("testName", "HbA1c", "result", "7.5"),
                        Map.of("testName", "Glucose", "result", "102")
                ))
        );

        ClinicalDocumentExtraction extraction = adapter.adapt(payload, response());

        assertThat(extraction.labResults()).hasSize(2);
        assertThat(extraction.labResults().get(0).value()).isEqualTo("7.5");
        assertThat(extraction.qualityWarnings()).anyMatch(warning -> warning.contains("hba1c"));
        assertThat(extraction.providerMetadata()).containsEntry("selectedStructuredLabPath", "answer.labResults");
    }

    @Test
    void arbitraryObjectWrappersDoNotRequireAdapterAllowListEntries() {
        Map<String, Object> payload = new LinkedHashMap<>();
        putAtPath(payload, "answer.clinicalDocumentExtraction.labResults", rows());
        putAtPath(payload, "answer.payload.clinical.labResults", rows());

        assertThat(adapter.adapt(payload, response()).labResults()).containsExactlyElementsOf(
                adapter.adapt(Map.of("labResults", rows()), response()).labResults());
    }

    @Test
    void traversalStopsAfterFourWrapperLevelsAndIgnoresNestedArrays() {
        Map<String, Object> payload = new LinkedHashMap<>();
        putAtPath(payload, "a.b.c.d.labResults", rows());
        putAtPath(payload, "too.deep.a.b.c.labResults", rows());
        payload.put("unrelated", List.of(Map.of("labResults", rows())));

        ClinicalDocumentExtraction extraction = adapter.adapt(payload, response());

        assertThat(extraction.labResults()).hasSize(6);
        assertThat(extraction.providerMetadata()).containsEntry("selectedStructuredLabPath", "a.b.c.d.labResults");
    }

    private AiDraftResponse response() {
        return new AiDraftResponse(true, false, "ok", "GEMINI", "test", "narrative", Map.of(),
                BigDecimal.valueOf(0.91), List.of(), List.of(), "STOP");
    }

    private List<Map<String, Object>> rows() {
        return List.of(
                Map.of("testName", "HbA1c", "result", "5.7", "unit", "%", "referenceRange", "4.0 - 5.6", "flag", "High"),
                Map.of("testName", "Fasting Glucose", "result", "102", "unit", "mg/dL", "referenceRange", "70 - 99", "flag", "High"),
                Map.of("testName", "Total Cholesterol", "result", "206", "unit", "mg/dL", "flag", "High"),
                Map.of("testName", "LDL Cholesterol", "result", "132", "unit", "mg/dL", "flag", "High"),
                Map.of("testName", "HDL Cholesterol", "result", "46", "unit", "mg/dL", "flag", "Normal"),
                Map.of("testName", "Triglycerides", "result", "139", "unit", "mg/dL", "flag", "Normal")
        );
    }

    @SuppressWarnings("unchecked")
    private void putAtPath(Map<String, Object> root, String path, Object value) {
        String[] segments = path.split("\\.");
        Map<String, Object> current = root;
        for (int index = 0; index < segments.length - 1; index++) {
            Map<String, Object> child = new LinkedHashMap<>();
            current.put(segments[index], child);
            current = child;
        }
        current.put(segments[segments.length - 1], value);
    }
}
