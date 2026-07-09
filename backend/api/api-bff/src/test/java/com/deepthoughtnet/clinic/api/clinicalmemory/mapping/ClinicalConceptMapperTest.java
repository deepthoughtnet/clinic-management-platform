package com.deepthoughtnet.clinic.api.clinicalmemory.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentEntity;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClinicalConceptMapperTest {

    @Test
    void mapsDiabetesFollowUpLabValuesIntoNormalizedLongitudinalConcepts() {
        ClinicalDocumentEntity document = clinicalDocument(
                "Diabetes Follow-up Lab Report Retest 1",
                LocalDate.of(2026, 1, 8)
        );
        ClinicalConceptMapper mapper = new ClinicalConceptMapper();

        Map<String, Object> structuredJson = new LinkedHashMap<>();
        structuredJson.put("factualFindings", Map.of(
                "conditions", List.of(Map.of(
                        "canonicalKey", "diabetes_mellitus",
                        "label", "Diabetes Mellitus",
                        "evidenceText", "Known diabetic"
                )),
                "labResults", List.of(
                        Map.of("testName", "HbA1c", "canonicalKey", "hba1c", "value", "8.4", "unit", "%", "evidenceText", "HbA1c 8.4 % < 5.7 normal; > 6.5 diabetic High"),
                        Map.of("testName", "Random Blood Sugar", "canonicalKey", "blood_sugar", "value", "198", "unit", "mg/dL", "evidenceText", "Random Blood Sugar 198 mg/dL 70 - 140 High"),
                        Map.of("testName", "Total Cholesterol", "canonicalKey", "cholesterol", "value", "228", "unit", "mg/dL", "evidenceText", "Total Cholesterol 228 mg/dL < 200 High"),
                        Map.of("testName", "LDL Cholesterol", "canonicalKey", "ldl", "value", "152", "unit", "mg/dL", "evidenceText", "LDL Cholesterol 152 mg/dL < 100 High"),
                        Map.of("testName", "HDL Cholesterol", "canonicalKey", "hdl", "value", "39", "unit", "mg/dL", "evidenceText", "HDL Cholesterol 39 mg/dL > 40 Low"),
                        Map.of("testName", "Triglycerides", "canonicalKey", "triglycerides", "value", "238", "unit", "mg/dL", "evidenceText", "Triglycerides 238 mg/dL < 150 High")
                ),
                "riskFlags", List.of(
                        Map.of("canonicalKey", "diabetes_risk", "label", "Diabetes", "evidenceText", "HbA1c 8.4 % < 5.7 normal; > 6.5 diabetic High"),
                        Map.of("canonicalKey", "lipid_risk", "label", "Dyslipidemia", "evidenceText", "Total Cholesterol 228 mg/dL < 200 High")
                )
        ));

        List<ClinicalConceptMapper.MappedConcept> concepts = mapper.map(
                document,
                structuredJson,
                "",
                new java.math.BigDecimal("0.96")
        );

        assertThat(concepts).extracting(ClinicalConceptMapper.MappedConcept::family)
                .contains("CONDITION", "LAB_RESULT", "RISK_FLAG");
        assertThat(concepts).filteredOn(concept -> "CONDITION".equals(concept.family()))
                .extracting(ClinicalConceptMapper.MappedConcept::key)
                .contains("diabetes_mellitus");
        assertThat(concepts).filteredOn(concept -> "LAB_RESULT".equals(concept.family()))
                .extracting(ClinicalConceptMapper.MappedConcept::key)
                .contains("hba1c", "blood_sugar", "cholesterol", "ldl", "hdl", "triglycerides");
        assertThat(concepts).filteredOn(concept -> "LAB_RESULT".equals(concept.family()) && "cholesterol".equals(concept.key()))
                .extracting(ClinicalConceptMapper.MappedConcept::label)
                .contains("Total Cholesterol");
        assertThat(concepts).filteredOn(concept -> "LAB_RESULT".equals(concept.family()) && "ldl".equals(concept.key()))
                .extracting(ClinicalConceptMapper.MappedConcept::label)
                .contains("LDL Cholesterol");
        assertThat(concepts).filteredOn(concept -> "LAB_RESULT".equals(concept.family()) && "hdl".equals(concept.key()))
                .extracting(ClinicalConceptMapper.MappedConcept::label)
                .contains("HDL Cholesterol");
        assertThat(concepts).filteredOn(concept -> "RISK_FLAG".equals(concept.family()))
                .extracting(ClinicalConceptMapper.MappedConcept::label)
                .contains("Diabetes", "Dyslipidemia");
    }

    @Test
    void normalizesStructuredHbA1cWithoutPickingReferenceRangeNumbers() {
        ClinicalDocumentEntity document = clinicalDocument(
                "Diabetes Follow-up Lab Report",
                LocalDate.of(2026, 1, 8)
        );
        ClinicalConceptMapper mapper = new ClinicalConceptMapper();

        Map<String, Object> structuredJson = new LinkedHashMap<>();
        structuredJson.put("factualFindings", Map.of(
                "labResults", List.of(
                        Map.of(
                                "testName", "HbA1c",
                                "canonicalKey", "hba1c",
                                "value", "1",
                                "unit", "%",
                                "evidenceText", "HbA1c 8.4 % < 5.7 normal; > 6.5 diabetic High"
                        )
                )
        ));

        List<ClinicalConceptMapper.MappedConcept> concepts = mapper.map(
                document,
                structuredJson,
                "HbA1c 8.4 % < 5.7 normal; > 6.5 diabetic High",
                new java.math.BigDecimal("0.96")
        );

        assertThat(concepts).filteredOn(concept -> "LAB_RESULT".equals(concept.family()) && "hba1c".equals(concept.key()))
                .extracting(ClinicalConceptMapper.MappedConcept::valueText)
                .contains("8.4");
    }

    @Test
    void ignoresNarrativeSummaryFieldsWhenMappingLongitudinalFacts() {
        ClinicalDocumentEntity document = clinicalDocument(
                "Diabetes Follow-up Lab Report",
                LocalDate.of(2026, 1, 8)
        );
        ClinicalConceptMapper mapper = new ClinicalConceptMapper();

        Map<String, Object> structuredJson = new LinkedHashMap<>();
        structuredJson.put("summary", "Review the lab report with Rohan Sharma and adjust diabetes management plan.");
        structuredJson.put("suggestedActions", List.of("Review the lab report", "Consider adjustments"));
        structuredJson.put("recommendations", "Follow up in 2 weeks.");
        structuredJson.put("advice", "Monitor blood sugar");
        structuredJson.put("followUp", "Discuss plan");
        structuredJson.put("patientInstructions", "Take medicines regularly");
        structuredJson.put("answer", "Review the elevated HbA1c and recommend lifestyle modifications.");

        List<ClinicalConceptMapper.MappedConcept> concepts = mapper.map(
                document,
                structuredJson,
                "",
                new java.math.BigDecimal("0.96")
        );

        assertThat(concepts).isEmpty();
    }

    @Test
    void parsesStructuredAndOcrLabFactsWithoutUsingHemoglobinOrRecommendationText() {
        ClinicalDocumentEntity document = clinicalDocument(
                "Diabetes Follow-up Lab Report Retest 4",
                LocalDate.of(2026, 1, 8)
        );
        ClinicalConceptMapper mapper = new ClinicalConceptMapper();

        List<ClinicalConceptMapper.MappedConcept> concepts = mapper.map(
                document,
                Map.of("factualFindings", Map.of(
                        "labResults", List.of(
                                Map.of("testName", "HbA1c", "canonicalKey", "hba1c", "value", "8.4", "unit", "%", "evidenceText", "HbA1c 8.4 % < 5.7 normal; > 6.5 diabetic High"),
                                Map.of("testName", "Random Blood Sugar", "canonicalKey", "blood_sugar", "value", "198", "unit", "mg/dL", "evidenceText", "Random Blood Sugar 198 mg/dL 70 - 140 High"),
                                Map.of("testName", "Total Cholesterol", "canonicalKey", "cholesterol", "value", "228", "unit", "mg/dL", "evidenceText", "Total Cholesterol 228 mg/dL < 200 High"),
                                Map.of("testName", "LDL Cholesterol", "canonicalKey", "ldl", "value", "152", "unit", "mg/dL", "evidenceText", "LDL Cholesterol 152 mg/dL < 100 High"),
                                Map.of("testName", "HDL Cholesterol", "canonicalKey", "hdl", "value", "39", "unit", "mg/dL", "evidenceText", "HDL Cholesterol 39 mg/dL > 40 Low"),
                                Map.of("testName", "Triglycerides", "canonicalKey", "triglycerides", "value", "238", "unit", "mg/dL", "evidenceText", "Triglycerides 238 mg/dL < 150 High")
                        )
                )),
                """
                        Hemoglobin 14.1 g/dL
                        Review the elevated HbA1c and discuss abnormal lipid profile.
                        HbA1c 8.4 % < 5.7 normal; > 6.5 diabetic High
                        Random Blood Sugar 198 mg/dL 70 - 140 High
                        Total Cholesterol 228 mg/dL < 200 High
                        LDL Cholesterol 152 mg/dL < 100 High
                        HDL Cholesterol 39 mg/dL > 40 Low
                        Triglycerides 238 mg/dL < 150 High
                        """,
                new java.math.BigDecimal("0.96")
        );

        assertThat(concepts).filteredOn(concept -> "LAB_RESULT".equals(concept.family()) && "hba1c".equals(concept.key()))
                .extracting(ClinicalConceptMapper.MappedConcept::valueText)
                .containsExactly("8.4");
        assertThat(concepts).filteredOn(concept -> "LAB_RESULT".equals(concept.family()) && "blood_sugar".equals(concept.key()))
                .extracting(ClinicalConceptMapper.MappedConcept::valueText)
                .containsExactly("198");
        assertThat(concepts).filteredOn(concept -> "LAB_RESULT".equals(concept.family()) && "cholesterol".equals(concept.key()))
                .extracting(ClinicalConceptMapper.MappedConcept::valueText)
                .containsExactly("228");
        assertThat(concepts).filteredOn(concept -> "LAB_RESULT".equals(concept.family()) && "ldl".equals(concept.key()))
                .extracting(ClinicalConceptMapper.MappedConcept::valueText)
                .containsExactly("152");
        assertThat(concepts).filteredOn(concept -> "LAB_RESULT".equals(concept.family()) && "hdl".equals(concept.key()))
                .extracting(ClinicalConceptMapper.MappedConcept::valueText)
                .containsExactly("39");
        assertThat(concepts).filteredOn(concept -> "LAB_RESULT".equals(concept.family()) && "triglycerides".equals(concept.key()))
                .extracting(ClinicalConceptMapper.MappedConcept::valueText)
                .containsExactly("238");
        assertThat(concepts).noneMatch(concept ->
                "LAB_RESULT".equals(concept.family())
                        && "hba1c".equals(concept.key())
                        && "14.1".equals(concept.valueText()));
        assertThat(concepts).noneMatch(concept ->
                "LAB_RESULT".equals(concept.family())
                        && "hba1c".equals(concept.key())
                        && "1".equals(concept.valueText()));
        assertThat(concepts).noneMatch(concept ->
                "CONDITION".equals(concept.family())
                        && concept.evidenceText() != null
                        && concept.evidenceText().toLowerCase().startsWith("review"));
    }

    @Test
    void ignoresRecommendationSectionsWhenFactualFindingsExist() {
        ClinicalDocumentEntity document = clinicalDocument(
                "Diabetes Follow-up Lab Report Retest 4",
                LocalDate.of(2026, 1, 8)
        );
        ClinicalConceptMapper mapper = new ClinicalConceptMapper();

        List<ClinicalConceptMapper.MappedConcept> concepts = mapper.map(
                document,
                Map.of(
                        "factualFindings", Map.of(
                                "labResults", List.of(
                                        Map.of("testName", "HbA1c", "canonicalKey", "hba1c", "value", "8.4", "unit", "%", "evidenceText", "HbA1c 8.4 % < 5.7 normal; > 6.5 diabetic High")
                                ),
                                "conditions", List.of(
                                        Map.of("canonicalKey", "diabetes_mellitus", "label", "Diabetes Mellitus", "evidenceText", "Known diabetic")
                                )
                        ),
                        "summary", "Review the lab report and adjust medications.",
                        "recommendations", List.of("Recommend lifestyle modifications"),
                        "suggestedActions", List.of("Discuss abnormal lipid profile")
                ),
                "Hemoglobin 14.1 g/dL\nReview the elevated HbA1c",
                new java.math.BigDecimal("0.96")
        );

        assertThat(concepts).extracting(ClinicalConceptMapper.MappedConcept::key)
                .contains("hba1c", "diabetes_mellitus")
                .doesNotContain("blood_sugar");
        assertThat(concepts).noneMatch(concept -> concept.evidenceText() != null
                && concept.evidenceText().toLowerCase().startsWith("review"));
    }

    private ClinicalDocumentEntity clinicalDocument(String title, LocalDate reportDate) {
        return ClinicalDocumentEntity.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                UUID.randomUUID(),
                ClinicalDocumentType.EXTERNAL_LAB_REPORT,
                title,
                "Lab report",
                reportDate,
                "Reception",
                "RECEPTION",
                "report.pdf",
                "application/pdf",
                1024,
                "bucket",
                "storage-key",
                "checksum",
                "INTERNAL_ONLY",
                "UNVERIFIED",
                "COMPLETED",
                "COMPLETED",
                "CONSULTATION",
                "consult-1",
                UUID.randomUUID(),
                UUID.randomUUID()
        );
    }
}
