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
        structuredJson.put("knownConditions", List.of("Diabetes Mellitus"));
        Map<String, Object> labs = new LinkedHashMap<>();
        labs.put("hba1c", "8.4%");
        labs.put("randomBloodSugar", "198 mg/dL");
        labs.put("totalCholesterol", "228 mg/dL");
        labs.put("ldlCholesterol", "152 mg/dL");
        labs.put("hdlCholesterol", "39 mg/dL");
        labs.put("triglycerides", "238 mg/dL");
        structuredJson.put("labs", labs);

        List<ClinicalConceptMapper.MappedConcept> concepts = mapper.map(
                document,
                structuredJson,
                """
                        known diabetic
                        HbA1c 8.4%
                        Random Blood Sugar 198 mg/dL
                        Total Cholesterol 228 mg/dL
                        LDL 152 mg/dL
                        HDL 39 mg/dL
                        Triglycerides 238 mg/dL
                        """,
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
