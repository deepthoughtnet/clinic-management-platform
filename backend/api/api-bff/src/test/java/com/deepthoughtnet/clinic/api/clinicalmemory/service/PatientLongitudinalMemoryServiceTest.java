package com.deepthoughtnet.clinic.api.clinicalmemory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentEntity;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import com.deepthoughtnet.clinic.api.clinicalmemory.db.PatientLongitudinalConceptEntity;
import com.deepthoughtnet.clinic.api.clinicalmemory.db.PatientLongitudinalConceptRepository;
import com.deepthoughtnet.clinic.api.clinicalmemory.mapping.ClinicalConceptMapper;
import com.deepthoughtnet.clinic.api.clinicalmemory.model.LongitudinalConceptSnapshot;
import com.deepthoughtnet.clinic.api.clinicalmemory.model.PatientLongitudinalMemoryProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PatientLongitudinalMemoryServiceTest {

    @Test
    void ingestPendingConceptsCreatesPendingLongitudinalMemory() {
        PatientLongitudinalConceptRepository repository = mock(PatientLongitudinalConceptRepository.class);
        AtomicReference<List<PatientLongitudinalConceptEntity>> saved = new AtomicReference<>(List.of());
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<PatientLongitudinalConceptEntity> value = (List<PatientLongitudinalConceptEntity>) invocation.getArgument(0);
            saved.set(value);
            return value;
        }).when(repository).saveAllAndFlush(anyList());
        when(repository.deleteByDocumentAndStatus(any(), any(), any(), any())).thenReturn(1);

        PatientLongitudinalMemoryService service = new PatientLongitudinalMemoryService(repository, new ClinicalConceptMapper(), new ObjectMapper());
        ClinicalDocumentEntity document = document("Diabetes Follow-up Laboratory Report", LocalDate.of(2026, 1, 8));

        service.ingestPendingConcepts(
                document,
                """
                        {"factualFindings":{
                          "conditions":[{"canonicalKey":"diabetes_mellitus","label":"Diabetes Mellitus","evidenceText":"Known diabetic"}],
                          "labResults":[
                            {"canonicalKey":"hba1c","testName":"HbA1c","value":"8.4","unit":"%","evidenceText":"HbA1c 8.4 % < 5.7 normal; > 6.5 diabetic High"},
                            {"canonicalKey":"blood_sugar","testName":"Random Blood Sugar","value":"198","unit":"mg/dL","evidenceText":"Random Blood Sugar 198 mg/dL 70 - 140 High"},
                            {"canonicalKey":"cholesterol","testName":"Total Cholesterol","value":"228","unit":"mg/dL","evidenceText":"Total Cholesterol 228 mg/dL < 200 High"},
                            {"canonicalKey":"ldl","testName":"LDL Cholesterol","value":"152","unit":"mg/dL","evidenceText":"LDL Cholesterol 152 mg/dL < 100 High"},
                            {"canonicalKey":"hdl","testName":"HDL Cholesterol","value":"39","unit":"mg/dL","evidenceText":"HDL Cholesterol 39 mg/dL > 40 Low"},
                            {"canonicalKey":"triglycerides","testName":"Triglycerides","value":"238","unit":"mg/dL","evidenceText":"Triglycerides 238 mg/dL < 150 High"}
                          ],
                          "riskFlags":[
                            {"canonicalKey":"diabetes_risk","label":"Diabetes","evidenceText":"HbA1c 8.4 % < 5.7 normal; > 6.5 diabetic High"},
                            {"canonicalKey":"lipid_risk","label":"Dyslipidemia","evidenceText":"Total Cholesterol 228 mg/dL < 200 High"}
                          ]
                        }}
                        """,
                "Known diabetic\nHbA1c 8.4\nRandom Blood Sugar 198\nLipid profile high cholesterol high LDL high triglycerides low HDL",
                new BigDecimal("0.96"),
                "Clinical extraction"
        );

        assertThat(saved.get()).isNotEmpty();
        assertThat(saved.get()).filteredOn(concept -> "PENDING_REVIEW".equals(concept.getVerificationStatus())).isNotEmpty();
        assertThat(saved.get()).extracting(PatientLongitudinalConceptEntity::getConceptKey)
                .contains("diabetes_mellitus", "hba1c", "blood_sugar", "cholesterol", "ldl", "triglycerides", "hdl", "diabetes_risk", "lipid_risk");
        assertThat(saved.get().stream().filter(concept -> "diabetes_mellitus".equals(concept.getConceptKey())).count()).isEqualTo(1);
        assertThat(saved.get().stream()
                .filter(concept -> "ldl".equals(concept.getConceptKey()))
                .map(PatientLongitudinalConceptEntity::getValueText))
                .contains("152");
        verify(repository).deleteByDocumentAndStatus(eq(document.getTenantId()), eq(document.getPatientId()), eq(document.getId()), eq("PENDING_REVIEW"));
    }

    @Test
    void ingestPendingConceptsReplacesExistingPendingConceptsForSameDocument() {
        PatientLongitudinalConceptRepository repository = mock(PatientLongitudinalConceptRepository.class);
        List<PatientLongitudinalConceptEntity> persisted = new ArrayList<>();
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<PatientLongitudinalConceptEntity> value = (List<PatientLongitudinalConceptEntity>) invocation.getArgument(0);
            persisted.removeIf(concept -> concept.getSourceDocumentId() != null && concept.getSourceDocumentId().equals(value.getFirst().getSourceDocumentId()));
            persisted.addAll(value);
            return value;
        }).when(repository).saveAllAndFlush(anyList());
        doAnswer(invocation -> {
            UUID sourceDocumentId = invocation.getArgument(2);
            persisted.removeIf(concept -> sourceDocumentId.equals(concept.getSourceDocumentId())
                    && "PENDING_REVIEW".equals(concept.getVerificationStatus()));
            return 1;
        }).when(repository).deleteByDocumentAndStatus(any(), any(), any(), any());

        PatientLongitudinalMemoryService service = new PatientLongitudinalMemoryService(repository, new ClinicalConceptMapper(), new ObjectMapper());
        ClinicalDocumentEntity document = document("Diabetes Follow-up Laboratory Report", LocalDate.of(2026, 1, 8));
        String extractedJson = "{\"conditions\":[\"Diabetes Mellitus\"],\"labs\":{\"hba1c\":\"8.4\",\"bloodSugar\":\"198\"}}";
        String ocrText = "Known diabetic\nHbA1c 8.4\nRandom Blood Sugar 198";

        service.ingestPendingConcepts(document, extractedJson, ocrText, new BigDecimal("0.96"), "Clinical extraction");
        service.ingestPendingConcepts(document, extractedJson, ocrText, new BigDecimal("0.96"), "Clinical extraction");

        assertThat(persisted).extracting(PatientLongitudinalConceptEntity::getVerificationStatus).containsOnly("PENDING_REVIEW");
        assertThat(persisted.stream().map(PatientLongitudinalConceptEntity::getConceptKey).toList())
                .contains("diabetes_mellitus", "hba1c", "blood_sugar", "diabetes_risk");
        assertThat(persisted).hasSize(4);
    }

    @Test
    void ingestPendingConceptsPreservesLongTextFields() {
        PatientLongitudinalConceptRepository repository = mock(PatientLongitudinalConceptRepository.class);
        AtomicReference<List<PatientLongitudinalConceptEntity>> saved = new AtomicReference<>(List.of());
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<PatientLongitudinalConceptEntity> value = (List<PatientLongitudinalConceptEntity>) invocation.getArgument(0);
            saved.set(value);
            return value;
        }).when(repository).saveAllAndFlush(anyList());
        when(repository.deleteByDocumentAndStatus(any(), any(), any(), any())).thenReturn(1);

        PatientLongitudinalMemoryService service = new PatientLongitudinalMemoryService(repository, new ClinicalConceptMapper(), new ObjectMapper());
        String longTitle = "Diabetes Follow-up Laboratory Report " + "A".repeat(280);
        ClinicalDocumentEntity document = document(longTitle, LocalDate.of(2026, 1, 8));
        String longSourceSummary = "Clinical extraction " + "C".repeat(300);

        service.ingestPendingConcepts(
                document,
                "{\"conditions\":[\"Diabetes Mellitus\"],\"labs\":{\"hba1c\":\"8.4\"}}",
                "Known diabetic\nHbA1c 8.4",
                new BigDecimal("0.96"),
                longSourceSummary
        );

        assertThat(saved.get()).isNotEmpty();
        assertThat(saved.get().getFirst().getSourceDocumentTitle()).startsWith("Diabetes Follow-up Laboratory Report");
        assertThat(saved.get().getFirst().getSourceDocumentTitle()).hasSizeGreaterThan(256);
        assertThat(saved.get().stream().map(PatientLongitudinalConceptEntity::getSourceSummary).anyMatch(text -> text != null && text.length() > 256)).isTrue();
        assertThat(saved.get().stream().map(PatientLongitudinalConceptEntity::getConceptLabel).allMatch(label -> label != null && label.length() <= 256)).isTrue();
    }

    @Test
    void repairPendingConceptsRebuildsStructuredLabFactsAndIgnoresRecommendationText() {
        PatientLongitudinalConceptRepository repository = mock(PatientLongitudinalConceptRepository.class);
        AtomicReference<List<PatientLongitudinalConceptEntity>> saved = new AtomicReference<>(List.of());
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<PatientLongitudinalConceptEntity> value = (List<PatientLongitudinalConceptEntity>) invocation.getArgument(0);
            saved.set(value);
            return value;
        }).when(repository).saveAllAndFlush(anyList());

        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        ClinicalDocumentEntity document = document(tenantId, patientId, "Diabetes Follow-up Lab Report Retest 4", LocalDate.of(2026, 1, 8));
        setField(document, "aiExtractionStructuredJson", """
                {
                  "factualFindings":{
                    "conditions":[{"canonicalKey":"diabetes_mellitus","label":"Diabetes Mellitus","evidenceText":"Known diabetic"}],
                    "labResults":[
                      {"canonicalKey":"hba1c","testName":"HbA1c","value":"8.4","unit":"%","evidenceText":"HbA1c 8.4 % < 5.7 normal; > 6.5 diabetic High"},
                      {"canonicalKey":"blood_sugar","testName":"Random Blood Sugar","value":"198","unit":"mg/dL","evidenceText":"Random Blood Sugar 198 mg/dL 70 - 140 High"},
                      {"canonicalKey":"cholesterol","testName":"Total Cholesterol","value":"228","unit":"mg/dL","evidenceText":"Total Cholesterol 228 mg/dL < 200 High"},
                      {"canonicalKey":"ldl","testName":"LDL Cholesterol","value":"152","unit":"mg/dL","evidenceText":"LDL Cholesterol 152 mg/dL < 100 High"},
                      {"canonicalKey":"hdl","testName":"HDL Cholesterol","value":"39","unit":"mg/dL","evidenceText":"HDL Cholesterol 39 mg/dL > 40 Low"},
                      {"canonicalKey":"triglycerides","testName":"Triglycerides","value":"238","unit":"mg/dL","evidenceText":"Triglycerides 238 mg/dL < 150 High"}
                    ],
                    "riskFlags":[
                      {"canonicalKey":"diabetes_risk","label":"Diabetes","evidenceText":"HbA1c 8.4 % < 5.7 normal; > 6.5 diabetic High"},
                      {"canonicalKey":"lipid_risk","label":"Dyslipidemia","evidenceText":"Total Cholesterol 228 mg/dL < 200 High"}
                    ]
                  },
                  "answer":"Review the elevated HbA1c and discuss abnormal lipid profile.",
                  "suggestedActions":["Discuss abnormal lipid profile","Recommend lifestyle modifications"]
                }
                """);
        setField(document, "aiExtractionSummary", "AI draft generated.");
        setField(document, "aiExtractionConfidence", new BigDecimal("0.91"));

        when(repository.findByTenantIdAndPatientIdAndSourceDocumentIdOrderByCreatedAtAsc(eq(tenantId), eq(patientId), eq(document.getId())))
                .thenReturn(List.of(
                        pendingConcept(tenantId, patientId, document, "LAB_RESULT", "hba1c", "HbA1c", "14.1", "%", "Hemoglobin 14.1 g/dL", "AI draft generated."),
                        pendingConcept(tenantId, patientId, document, "LAB_RESULT", "blood_sugar", "Blood Sugar", "1", "mg/dL", "Review the elevated HbA1c and recommend lifestyle modifications.", "AI draft generated."),
                        pendingConcept(tenantId, patientId, document, "CONDITION", "diabetes_mellitus", "Diabetes Mellitus", "Review the lab report with Rohan Sharma and adjust diabetes management plan.", null, "Review the lab report with Rohan Sharma and adjust diabetes management plan.", "AI draft generated.")
                ));
        when(repository.deleteByDocumentAndStatus(any(), any(), any(), any())).thenReturn(3);

        PatientLongitudinalMemoryService service = new PatientLongitudinalMemoryService(repository, new ClinicalConceptMapper(), new ObjectMapper());
        var result = service.repairPendingConcepts(document, document.getAiExtractionStructuredJson(), null, new BigDecimal("0.91"), "AI draft generated.", UUID.randomUUID());

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.correctedValues()).extracting("conceptKey", "oldValue", "newValue")
                .contains(
                        org.assertj.core.groups.Tuple.tuple("hba1c", "14.1", "8.4"),
                        org.assertj.core.groups.Tuple.tuple("blood_sugar", "1", "198")
                );
        assertThat(result.message()).contains("Memory repaired");
        assertThat(saved.get()).extracting(PatientLongitudinalConceptEntity::getConceptKey)
                .contains("diabetes_mellitus", "hba1c", "blood_sugar", "cholesterol", "ldl", "hdl", "triglycerides", "diabetes_risk", "lipid_risk");
        assertThat(saved.get()).noneMatch(concept -> concept.getEvidenceText() != null && concept.getEvidenceText().toLowerCase().startsWith("review"));
        assertThat(saved.get()).filteredOn(concept -> "LAB_RESULT".equals(concept.getConceptFamily()) && "hba1c".equals(concept.getConceptKey()))
                .extracting(PatientLongitudinalConceptEntity::getValueText)
                .containsExactly("8.4");
        assertThat(saved.get()).filteredOn(concept -> "LAB_RESULT".equals(concept.getConceptFamily()) && "blood_sugar".equals(concept.getConceptKey()))
                .extracting(PatientLongitudinalConceptEntity::getValueText)
                .containsExactly("198");
        verify(repository).deleteByDocumentAndStatus(eq(tenantId), eq(patientId), eq(document.getId()), eq("PENDING_REVIEW"));
    }

    @Test
    void repairPendingConceptsMergesParsedHbA1cIntoPartialStructuredLabFacts() {
        PatientLongitudinalConceptRepository repository = mock(PatientLongitudinalConceptRepository.class);
        AtomicReference<List<PatientLongitudinalConceptEntity>> saved = new AtomicReference<>(List.of());
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<PatientLongitudinalConceptEntity> value = (List<PatientLongitudinalConceptEntity>) invocation.getArgument(0);
            saved.set(value);
            return value;
        }).when(repository).saveAllAndFlush(anyList());
        when(repository.deleteByDocumentAndStatus(any(), any(), any(), any())).thenReturn(1);

        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        ClinicalDocumentEntity document = document(tenantId, patientId, "HbA1c follow-up report", LocalDate.of(2026, 1, 15));
        String structuredJson = """
                {
                  "factualFindings":{
                    "labResults":[
                      {"canonicalKey":"estimated_average_glucose","testName":"Estimated Average Glucose","value":"163","unit":"mg/dL","evidenceText":"Estimated Average Glucose 163 mg/dL"}
                    ]
                  }
                }
                """;
        String sourceText = """
                Test | Result
                HbA1c | 7.3 %
                Estimated Average Glucose | 163 mg/dL
                """;

        PatientLongitudinalMemoryService service = new PatientLongitudinalMemoryService(repository, new ClinicalConceptMapper(), new ObjectMapper());
        var result = service.repairPendingConcepts(document, structuredJson, sourceText, new BigDecimal("0.91"), "AI draft generated.", UUID.randomUUID());

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(saved.get()).extracting(PatientLongitudinalConceptEntity::getConceptKey)
                .contains("hba1c", "estimated_average_glucose");
        assertThat(saved.get()).filteredOn(concept -> "LAB_RESULT".equals(concept.getConceptFamily()) && "hba1c".equals(concept.getConceptKey()))
                .extracting(PatientLongitudinalConceptEntity::getValueText, PatientLongitudinalConceptEntity::getValueUnit)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("7.3", "%"));
        assertThat(saved.get()).filteredOn(concept -> "LAB_RESULT".equals(concept.getConceptFamily()) && "estimated_average_glucose".equals(concept.getConceptKey()))
                .extracting(PatientLongitudinalConceptEntity::getValueText, PatientLongitudinalConceptEntity::getValueUnit)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("163", "mg/dL"));
    }

    @Test
    void repairPendingConceptsPersistsHbA1cAndHemoglobinAsSeparateLabs() {
        PatientLongitudinalConceptRepository repository = mock(PatientLongitudinalConceptRepository.class);
        AtomicReference<List<PatientLongitudinalConceptEntity>> saved = new AtomicReference<>(List.of());
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<PatientLongitudinalConceptEntity> value = (List<PatientLongitudinalConceptEntity>) invocation.getArgument(0);
            saved.set(value);
            return value;
        }).when(repository).saveAllAndFlush(anyList());
        when(repository.deleteByDocumentAndStatus(any(), any(), any(), any())).thenReturn(1);

        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        ClinicalDocumentEntity document = document(tenantId, patientId, "HbA1c follow-up report", LocalDate.of(2026, 1, 15));
        String structuredJson = """
                {
                  "factualFindings":{
                    "labResults":[
                      {"canonicalKey":"estimated_average_glucose","testName":"Estimated Average Glucose","value":"163","unit":"mg/dL","evidenceText":"Estimated Average Glucose 163 mg/dL"}
                    ]
                  }
                }
                """;
        String sourceText = """
                HbA1c | 7.3 %
                Hemoglobin | 14.1 g/dL
                Estimated Average Glucose | 163 mg/dL
                """;

        PatientLongitudinalMemoryService service = new PatientLongitudinalMemoryService(repository, new ClinicalConceptMapper(), new ObjectMapper());
        var result = service.repairPendingConcepts(document, structuredJson, sourceText, new BigDecimal("0.91"), "AI draft generated.", UUID.randomUUID());

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(saved.get()).extracting(PatientLongitudinalConceptEntity::getConceptKey)
                .containsExactlyInAnyOrder("hba1c", "hemoglobin", "estimated_average_glucose", "diabetes_risk");
        assertThat(saved.get()).filteredOn(concept -> "LAB_RESULT".equals(concept.getConceptFamily()) && "hba1c".equals(concept.getConceptKey()))
                .extracting(PatientLongitudinalConceptEntity::getValueText, PatientLongitudinalConceptEntity::getValueUnit)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("7.3", "%"));
        assertThat(saved.get()).filteredOn(concept -> "LAB_RESULT".equals(concept.getConceptFamily()) && "hemoglobin".equals(concept.getConceptKey()))
                .extracting(PatientLongitudinalConceptEntity::getValueText, PatientLongitudinalConceptEntity::getValueUnit)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("14.1", "g/dL"));
        assertThat(saved.get()).noneMatch(concept -> "LAB_RESULT".equals(concept.getConceptFamily())
                && "hba1c".equals(concept.getConceptKey())
                && "14.1".equals(concept.getValueText()));
    }

    @Test
    void repairPendingConceptsIsIdempotentForSameDocument() {
        PatientLongitudinalConceptRepository repository = mock(PatientLongitudinalConceptRepository.class);
        List<PatientLongitudinalConceptEntity> persisted = new ArrayList<>();
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<PatientLongitudinalConceptEntity> value = (List<PatientLongitudinalConceptEntity>) invocation.getArgument(0);
            persisted.removeIf(concept -> concept.getSourceDocumentId() != null && concept.getSourceDocumentId().equals(value.getFirst().getSourceDocumentId()));
            persisted.addAll(value);
            return value;
        }).when(repository).saveAllAndFlush(anyList());
        doAnswer(invocation -> {
            UUID sourceDocumentId = invocation.getArgument(2);
            persisted.removeIf(concept -> sourceDocumentId.equals(concept.getSourceDocumentId())
                    && "PENDING_REVIEW".equals(concept.getVerificationStatus()));
            return 1;
        }).when(repository).deleteByDocumentAndStatus(any(), any(), any(), any());

        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        ClinicalDocumentEntity document = document(tenantId, patientId, "HbA1c follow-up report", LocalDate.of(2026, 1, 15));
        String structuredJson = """
                {
                  "factualFindings":{
                    "labResults":[
                      {"canonicalKey":"estimated_average_glucose","testName":"Estimated Average Glucose","value":"163","unit":"mg/dL","evidenceText":"Estimated Average Glucose 163 mg/dL"}
                    ]
                  }
                }
                """;
        String sourceText = """
                HbA1c | 7.3 %
                Estimated Average Glucose | 163 mg/dL
                """;

        PatientLongitudinalMemoryService service = new PatientLongitudinalMemoryService(repository, new ClinicalConceptMapper(), new ObjectMapper());
        service.repairPendingConcepts(document, structuredJson, sourceText, new BigDecimal("0.91"), "AI draft generated.", UUID.randomUUID());
        service.repairPendingConcepts(document, structuredJson, sourceText, new BigDecimal("0.91"), "AI draft generated.", UUID.randomUUID());

        assertThat(persisted).extracting(PatientLongitudinalConceptEntity::getConceptKey)
                .containsExactlyInAnyOrder("hba1c", "estimated_average_glucose", "diabetes_risk");
        assertThat(persisted).hasSize(3);
    }

    @Test
    void repairPendingConceptsUsesStoredStructuredKidneyLabsWithoutSourceTextAndNormalizesEgfrUnit() {
        PatientLongitudinalConceptRepository repository = mock(PatientLongitudinalConceptRepository.class);
        AtomicReference<List<PatientLongitudinalConceptEntity>> saved = new AtomicReference<>(List.of());
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<PatientLongitudinalConceptEntity> value = (List<PatientLongitudinalConceptEntity>) invocation.getArgument(0);
            saved.set(value);
            return value;
        }).when(repository).saveAllAndFlush(anyList());
        when(repository.deleteByDocumentAndStatus(any(), any(), any(), any())).thenReturn(1);

        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        ClinicalDocumentEntity document = document(tenantId, patientId, "Kidney Function Report", LocalDate.of(2026, 5, 20));
        String structuredJson = """
                {
                  "factualFindings":{
                    "labResults":[
                      {"canonicalKey":"creatinine","testName":"Creatinine","value":"1.08","unit":"mg/dL"},
                      {"canonicalKey":"egfr","testName":"eGFR","value":"84","unit":"mL/min/1.73m²"}
                    ]
                  }
                }
                """;

        PatientLongitudinalMemoryService service = new PatientLongitudinalMemoryService(repository, new ClinicalConceptMapper(), new ObjectMapper());
        var result = service.repairPendingConcepts(document, structuredJson, "", new BigDecimal("0.18"), "Mock provider", UUID.randomUUID());

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.insertedConceptCount()).isEqualTo(2);
        assertThat(saved.get()).extracting(PatientLongitudinalConceptEntity::getConceptKey)
                .containsExactlyInAnyOrder("creatinine", "egfr");
        assertThat(saved.get()).filteredOn(concept -> "LAB_RESULT".equals(concept.getConceptFamily()) && "egfr".equals(concept.getConceptKey()))
                .extracting(PatientLongitudinalConceptEntity::getValueUnit)
                .containsExactly("mL/min/1.73m2");
        assertThat(saved.get()).extracting(PatientLongitudinalConceptEntity::getVerificationStatus).containsOnly("PENDING_REVIEW");
    }

    @Test
    void repairPendingConceptsMergesParsedEgfrIntoPartialStructuredKidneyLabs() {
        PatientLongitudinalConceptRepository repository = mock(PatientLongitudinalConceptRepository.class);
        AtomicReference<List<PatientLongitudinalConceptEntity>> saved = new AtomicReference<>(List.of());
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<PatientLongitudinalConceptEntity> value = (List<PatientLongitudinalConceptEntity>) invocation.getArgument(0);
            saved.set(value);
            return value;
        }).when(repository).saveAllAndFlush(anyList());
        when(repository.deleteByDocumentAndStatus(any(), any(), any(), any())).thenReturn(1);

        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        ClinicalDocumentEntity document = document(tenantId, patientId, "Kidney Function Report", LocalDate.of(2026, 5, 20));
        String structuredJson = """
                {
                  "factualFindings":{
                    "labResults":[
                      {"canonicalKey":"creatinine","testName":"Creatinine","value":"1.08","unit":"mg/dL"}
                    ]
                  }
                }
                """;
        String sourceText = """
                Creatinine 1.08 mg/dL
                eGFR 84 mL/min/1.73m²
                """;

        PatientLongitudinalMemoryService service = new PatientLongitudinalMemoryService(repository, new ClinicalConceptMapper(), new ObjectMapper());
        var result = service.repairPendingConcepts(document, structuredJson, sourceText, new BigDecimal("0.18"), "Mock provider", UUID.randomUUID());

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(saved.get()).extracting(PatientLongitudinalConceptEntity::getConceptKey)
                .containsExactlyInAnyOrder("creatinine", "egfr");
        assertThat(saved.get()).filteredOn(concept -> "LAB_RESULT".equals(concept.getConceptFamily()) && "egfr".equals(concept.getConceptKey()))
                .extracting(PatientLongitudinalConceptEntity::getValueText, PatientLongitudinalConceptEntity::getValueUnit)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("84", "mL/min/1.73m2"));
    }

    @Test
    void repairPendingConceptsReturnsFailureWhenNoFactualLabRowsExist() {
        PatientLongitudinalConceptRepository repository = mock(PatientLongitudinalConceptRepository.class);
        when(repository.deleteByDocumentAndStatus(any(), any(), any(), any())).thenReturn(0);

        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        ClinicalDocumentEntity document = document(tenantId, patientId, "Kidney Function Report", LocalDate.of(2026, 5, 20));

        PatientLongitudinalMemoryService service = new PatientLongitudinalMemoryService(repository, new ClinicalConceptMapper(), new ObjectMapper());
        var result = service.repairPendingConcepts(document, "{}", "", new BigDecimal("0.18"), "Mock provider", UUID.randomUUID());

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.message()).isEqualTo("No factual lab rows available for memory repair.");
        verify(repository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void verifyAndProfileKeepsHistoryAndUsesLatestAcceptedValues() {
        PatientLongitudinalConceptRepository repository = mock(PatientLongitudinalConceptRepository.class);
        PatientLongitudinalMemoryService service = new PatientLongitudinalMemoryService(repository, new ClinicalConceptMapper(), new ObjectMapper());

        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        ClinicalDocumentEntity olderDocument = document(tenantId, patientId, "Older report", LocalDate.of(2025, 11, 1));
        ClinicalDocumentEntity newerDocument = document(tenantId, patientId, "Newer report", LocalDate.of(2026, 1, 8));

        PatientLongitudinalConceptEntity acceptedOlderHba1c = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                olderDocument.getId(),
                olderDocument.getDocumentType().name(),
                olderDocument.getTitle(),
                olderDocument.getReportDate(),
                "LAB_RESULT",
                "hba1c",
                "HbA1c",
                "7.6",
                "%",
                "HbA1c 7.6",
                "Older report",
                "ACCEPTED",
                new BigDecimal("0.91"),
                olderDocument.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        PatientLongitudinalConceptEntity acceptedBloodSugar = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                newerDocument.getId(),
                newerDocument.getDocumentType().name(),
                newerDocument.getTitle(),
                newerDocument.getReportDate(),
                "LAB_RESULT",
                "blood_sugar",
                "Blood Sugar",
                "198",
                "mg/dL",
                "Random Blood Sugar 198",
                "Newer report",
                "ACCEPTED",
                new BigDecimal("0.96"),
                newerDocument.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        PatientLongitudinalConceptEntity acceptedNewerHba1c = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                newerDocument.getId(),
                newerDocument.getDocumentType().name(),
                newerDocument.getTitle(),
                newerDocument.getReportDate(),
                "LAB_RESULT",
                "hba1c",
                "HbA1c",
                "8.4",
                "%",
                "HbA1c 8.4",
                "Newer report",
                "ACCEPTED",
                new BigDecimal("0.96"),
                newerDocument.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        PatientLongitudinalConceptEntity acceptedCondition = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                newerDocument.getId(),
                newerDocument.getDocumentType().name(),
                newerDocument.getTitle(),
                newerDocument.getReportDate(),
                "CONDITION",
                "diabetes_mellitus",
                "Diabetes Mellitus",
                "Diabetes Mellitus",
                null,
                "Known diabetic",
                "Newer report",
                "ACCEPTED",
                new BigDecimal("0.96"),
                newerDocument.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        PatientLongitudinalConceptEntity acceptedTotalCholesterol = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                newerDocument.getId(),
                newerDocument.getDocumentType().name(),
                newerDocument.getTitle(),
                newerDocument.getReportDate(),
                "LAB_RESULT",
                "cholesterol",
                "Total Cholesterol",
                "228",
                "mg/dL",
                "Total Cholesterol 228",
                "Newer report",
                "ACCEPTED",
                new BigDecimal("0.96"),
                newerDocument.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        PatientLongitudinalConceptEntity acceptedLdl = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                newerDocument.getId(),
                newerDocument.getDocumentType().name(),
                newerDocument.getTitle(),
                newerDocument.getReportDate(),
                "LAB_RESULT",
                "ldl",
                "LDL Cholesterol",
                "152",
                "mg/dL",
                "LDL 152",
                "Newer report",
                "ACCEPTED",
                new BigDecimal("0.96"),
                newerDocument.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        PatientLongitudinalConceptEntity acceptedHdl = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                newerDocument.getId(),
                newerDocument.getDocumentType().name(),
                newerDocument.getTitle(),
                newerDocument.getReportDate(),
                "LAB_RESULT",
                "hdl",
                "HDL Cholesterol",
                "39",
                "mg/dL",
                "HDL 39",
                "Newer report",
                "ACCEPTED",
                new BigDecimal("0.96"),
                newerDocument.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        PatientLongitudinalConceptEntity acceptedTriglycerides = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                newerDocument.getId(),
                newerDocument.getDocumentType().name(),
                newerDocument.getTitle(),
                newerDocument.getReportDate(),
                "LAB_RESULT",
                "triglycerides",
                "Triglycerides",
                "238",
                "mg/dL",
                "Triglycerides 238",
                "Newer report",
                "ACCEPTED",
                new BigDecimal("0.96"),
                newerDocument.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        PatientLongitudinalConceptEntity acceptedDiabetesRisk = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                newerDocument.getId(),
                newerDocument.getDocumentType().name(),
                newerDocument.getTitle(),
                newerDocument.getReportDate(),
                "RISK_FLAG",
                "diabetes_risk",
                "Diabetes",
                "Diabetes",
                null,
                "Known diabetic",
                "Newer report",
                "ACCEPTED",
                new BigDecimal("0.96"),
                newerDocument.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        PatientLongitudinalConceptEntity acceptedLipidRisk = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                newerDocument.getId(),
                newerDocument.getDocumentType().name(),
                newerDocument.getTitle(),
                newerDocument.getReportDate(),
                "RISK_FLAG",
                "lipid_risk",
                "Dyslipidemia",
                "Dyslipidemia",
                null,
                "High LDL",
                "Newer report",
                "ACCEPTED",
                new BigDecimal("0.96"),
                newerDocument.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        PatientLongitudinalConceptEntity rejectedConcept = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                newerDocument.getId(),
                newerDocument.getDocumentType().name(),
                newerDocument.getTitle(),
                newerDocument.getReportDate(),
                "LAB_RESULT",
                "blood_sugar",
                "Blood Sugar",
                "198",
                "mg/dL",
                "Random Blood Sugar 198",
                "Newer report",
                "REJECTED",
                new BigDecimal("0.96"),
                newerDocument.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        rejectedConcept.markVerified("REJECTED", UUID.randomUUID(), "Rejected", null);

        when(repository.findByTenantIdAndPatientIdOrderByObservedAtDescCreatedAtDesc(tenantId, patientId)).thenReturn(List.of(
                acceptedNewerHba1c,
                acceptedBloodSugar,
                acceptedCondition,
                acceptedTotalCholesterol,
                acceptedLdl,
                acceptedHdl,
                acceptedTriglycerides,
                acceptedDiabetesRisk,
                acceptedLipidRisk,
                rejectedConcept,
                acceptedOlderHba1c
        ));

        PatientLongitudinalMemoryProfile profile = service.buildProfile(tenantId, patientId);

        assertThat(profile.knownConditions()).hasSize(1);
        assertThat(profile.longTermMedications()).isEmpty();
        assertThat(profile.latestHbA1c()).isNotNull();
        assertThat(profile.latestHbA1c().valueText()).isEqualTo("8.4");
        assertThat(profile.latestBloodSugar()).isNotNull();
        assertThat(profile.latestBloodSugar().valueText()).isEqualTo("198");
        assertThat(profile.latestLipidSummary()).extracting(LongitudinalConceptSnapshot::label)
                .contains("Total Cholesterol", "LDL Cholesterol", "HDL Cholesterol", "Triglycerides");
        assertThat(profile.latestLipidSummary()).extracting(LongitudinalConceptSnapshot::valueText)
                .contains("228", "152", "39", "238");
        assertThat(profile.riskFlags()).extracting(LongitudinalConceptSnapshot::label).contains("Diabetes", "Dyslipidemia");
        assertThat(profile.history()).hasSize(10);
        assertThat(profile.mostRecentLaboratorySummary()).contains("HbA1c 8.4");

        when(repository.findByTenantIdAndPatientIdAndSourceDocumentIdOrderByCreatedAtAsc(tenantId, patientId, newerDocument.getId()))
                .thenReturn(List.of(
                        PatientLongitudinalConceptEntity.create(
                                tenantId,
                                patientId,
                                newerDocument.getId(),
                                newerDocument.getDocumentType().name(),
                                newerDocument.getTitle(),
                                newerDocument.getReportDate(),
                                "LAB_RESULT",
                                "hba1c",
                                "HbA1c",
                                "8.4",
                                "%",
                                "HbA1c 8.4",
                                "Newer report",
                                "PENDING_REVIEW",
                                new BigDecimal("0.96"),
                                newerDocument.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
                        )
                ));

        service.verifyDocumentConcepts(newerDocument, true, UUID.randomUUID(), "{\"hba1c\":\"8.4\"}", "Accepted", null);
        verify(repository).saveAllAndFlush(anyList());
    }

    @Test
    void buildProfileSurfacesPendingConceptsWithoutDiscardingLabs() {
        PatientLongitudinalConceptRepository repository = mock(PatientLongitudinalConceptRepository.class);
        PatientLongitudinalMemoryService service = new PatientLongitudinalMemoryService(repository, new ClinicalConceptMapper(), new ObjectMapper());

        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        ClinicalDocumentEntity document = document(tenantId, patientId, "Diabetes Follow-up Lab Report", LocalDate.of(2026, 1, 8));

        PatientLongitudinalConceptEntity pendingCondition = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                document.getId(),
                document.getDocumentType().name(),
                document.getTitle(),
                document.getReportDate(),
                "CONDITION",
                "diabetes_mellitus",
                "Diabetes Mellitus",
                "Diabetes Mellitus",
                null,
                "Known diabetic",
                "Clinical extraction",
                "PENDING_REVIEW",
                new BigDecimal("0.96"),
                document.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        PatientLongitudinalConceptEntity pendingHbA1c = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                document.getId(),
                document.getDocumentType().name(),
                document.getTitle(),
                document.getReportDate(),
                "LAB_RESULT",
                "hba1c",
                "HbA1c",
                "8.4",
                "%",
                "HbA1c 8.4",
                "Clinical extraction",
                "PENDING_REVIEW",
                new BigDecimal("0.96"),
                document.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        PatientLongitudinalConceptEntity pendingBloodSugar = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                document.getId(),
                document.getDocumentType().name(),
                document.getTitle(),
                document.getReportDate(),
                "LAB_RESULT",
                "blood_sugar",
                "Blood Sugar",
                "198",
                "mg/dL",
                "Random Blood Sugar 198",
                "Clinical extraction",
                "PENDING_REVIEW",
                new BigDecimal("0.96"),
                document.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        PatientLongitudinalConceptEntity pendingLdl = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                document.getId(),
                document.getDocumentType().name(),
                document.getTitle(),
                document.getReportDate(),
                "LAB_RESULT",
                "ldl",
                "LDL",
                "152",
                "mg/dL",
                "LDL 152",
                "Clinical extraction",
                "PENDING_REVIEW",
                new BigDecimal("0.96"),
                document.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        PatientLongitudinalConceptEntity pendingRisk = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                document.getId(),
                document.getDocumentType().name(),
                document.getTitle(),
                document.getReportDate(),
                "RISK_FLAG",
                "diabetes_risk",
                "Diabetes",
                "Diabetes",
                null,
                "Known diabetic",
                "Clinical extraction",
                "PENDING_REVIEW",
                new BigDecimal("0.96"),
                document.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );

        when(repository.findByTenantIdAndPatientIdOrderByObservedAtDescCreatedAtDesc(tenantId, patientId)).thenReturn(List.of(
                pendingRisk,
                pendingLdl,
                pendingBloodSugar,
                pendingHbA1c,
                pendingCondition
        ));

        PatientLongitudinalMemoryProfile profile = service.buildProfile(tenantId, patientId);

        assertThat(profile.knownConditions()).extracting(concept -> concept.verificationStatus()).containsExactly("PENDING_REVIEW");
        assertThat(profile.latestHbA1c()).isNotNull();
        assertThat(profile.latestHbA1c().valueText()).isEqualTo("8.4");
        assertThat(profile.latestHbA1c().verificationStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(profile.latestBloodSugar()).isNotNull();
        assertThat(profile.latestBloodSugar().valueText()).isEqualTo("198");
        assertThat(profile.latestLipidSummary()).extracting(LongitudinalConceptSnapshot::conceptKey).contains("ldl");
        assertThat(profile.riskFlags()).extracting(LongitudinalConceptSnapshot::label).contains("Diabetes");
        assertThat(profile.history()).hasSize(5);
    }

    @Test
    void buildProfileFiltersNarrativeConditionLabelsAndPrefersAcceptedConcepts() {
        PatientLongitudinalConceptRepository repository = mock(PatientLongitudinalConceptRepository.class);
        PatientLongitudinalMemoryService service = new PatientLongitudinalMemoryService(repository, new ClinicalConceptMapper(), new ObjectMapper());

        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        ClinicalDocumentEntity document = document(tenantId, patientId, "Diabetes Follow-up Lab Report", LocalDate.of(2026, 1, 8));

        PatientLongitudinalConceptEntity pollutedRecommendation = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                document.getId(),
                document.getDocumentType().name(),
                document.getTitle(),
                document.getReportDate(),
                "CONDITION",
                "diabetes_mellitus",
                "Review the lab report with Rohan Sharma",
                "Review the lab report with Rohan Sharma",
                null,
                "Review the lab report with Rohan Sharma",
                "Clinical extraction",
                "PENDING_REVIEW",
                new BigDecimal("0.96"),
                document.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        PatientLongitudinalConceptEntity pendingHba1c = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                document.getId(),
                document.getDocumentType().name(),
                document.getTitle(),
                document.getReportDate(),
                "LAB_RESULT",
                "hba1c",
                "HbA1c",
                "8.4",
                "%",
                "HbA1c 8.4",
                "Clinical extraction",
                "PENDING_REVIEW",
                new BigDecimal("0.91"),
                document.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        PatientLongitudinalConceptEntity acceptedHba1c = PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                document.getId(),
                document.getDocumentType().name(),
                document.getTitle(),
                document.getReportDate(),
                "LAB_RESULT",
                "hba1c",
                "HbA1c",
                "8.4",
                "%",
                "HbA1c 8.4",
                "Clinical extraction",
                "ACCEPTED",
                new BigDecimal("0.99"),
                document.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );

        when(repository.findByTenantIdAndPatientIdOrderByObservedAtDescCreatedAtDesc(tenantId, patientId)).thenReturn(List.of(
                acceptedHba1c,
                pendingHba1c,
                pollutedRecommendation
        ));

        PatientLongitudinalMemoryProfile profile = service.buildProfile(tenantId, patientId);

        assertThat(profile.knownConditions()).isEmpty();
        assertThat(profile.latestHbA1c()).isNotNull();
        assertThat(profile.latestHbA1c().verificationStatus()).isEqualTo("ACCEPTED");
        assertThat(profile.history()).extracting(LongitudinalConceptSnapshot::conceptKey).containsExactly("hba1c");
    }

    @Test
    void buildProfilePrefersCleanFactualPendingConceptsOverPollutedRetestRows() {
        PatientLongitudinalConceptRepository repository = mock(PatientLongitudinalConceptRepository.class);
        PatientLongitudinalMemoryService service = new PatientLongitudinalMemoryService(repository, new ClinicalConceptMapper(), new ObjectMapper());

        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        ClinicalDocumentEntity pollutedDocument = document(tenantId, patientId, "Diabetes Follow-up Lab Report Retest 4", LocalDate.of(2026, 1, 8));
        ClinicalDocumentEntity cleanDocument = document(tenantId, patientId, "Diabetes Follow-up Lab Report Retest 5", LocalDate.of(2026, 1, 8));

        PatientLongitudinalConceptEntity pollutedBloodSugar = PatientLongitudinalConceptEntity.create(
                tenantId, patientId, pollutedDocument.getId(), pollutedDocument.getDocumentType().name(), pollutedDocument.getTitle(),
                pollutedDocument.getReportDate(), "LAB_RESULT", "blood_sugar", "Blood Sugar", "1", "mg/dL",
                "Review the elevated HbA1c and recommend lifestyle modifications.", "Polluted", "PENDING_REVIEW",
                new BigDecimal("0.99"), pollutedDocument.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        PatientLongitudinalConceptEntity pollutedHba1c = PatientLongitudinalConceptEntity.create(
                tenantId, patientId, pollutedDocument.getId(), pollutedDocument.getDocumentType().name(), pollutedDocument.getTitle(),
                pollutedDocument.getReportDate(), "LAB_RESULT", "hba1c", "HbA1c", "14.1", "%",
                "Hemoglobin 14.1 g/dL 13 - 17 Normal", "Polluted", "PENDING_REVIEW",
                new BigDecimal("0.99"), pollutedDocument.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        PatientLongitudinalConceptEntity cleanBloodSugar = PatientLongitudinalConceptEntity.create(
                tenantId, patientId, cleanDocument.getId(), cleanDocument.getDocumentType().name(), cleanDocument.getTitle(),
                cleanDocument.getReportDate(), "LAB_RESULT", "blood_sugar", "Blood Sugar", "198", "mg/dL",
                "Random Blood Sugar 198 mg/dL 70 - 140 High", "Clean", "PENDING_REVIEW",
                new BigDecimal("0.91"), cleanDocument.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
        PatientLongitudinalConceptEntity cleanHba1c = PatientLongitudinalConceptEntity.create(
                tenantId, patientId, cleanDocument.getId(), cleanDocument.getDocumentType().name(), cleanDocument.getTitle(),
                cleanDocument.getReportDate(), "LAB_RESULT", "hba1c", "HbA1c", "8.4", "%",
                "HbA1c 8.4 % < 5.7 normal; > 6.5 diabetic High", "Clean", "PENDING_REVIEW",
                new BigDecimal("0.91"), cleanDocument.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );

        when(repository.findByTenantIdAndPatientIdOrderByObservedAtDescCreatedAtDesc(tenantId, patientId))
                .thenReturn(List.of(pollutedBloodSugar, pollutedHba1c, cleanBloodSugar, cleanHba1c));

        PatientLongitudinalMemoryProfile profile = service.buildProfile(tenantId, patientId);

        assertThat(profile.latestHbA1c()).isNotNull();
        assertThat(profile.latestHbA1c().valueText()).isEqualTo("8.4");
        assertThat(profile.latestHbA1c().sourceDocumentTitle()).isEqualTo("Diabetes Follow-up Lab Report Retest 5");
        assertThat(profile.latestBloodSugar()).isNotNull();
        assertThat(profile.latestBloodSugar().valueText()).isEqualTo("198");
        assertThat(profile.latestBloodSugar().sourceDocumentTitle()).isEqualTo("Diabetes Follow-up Lab Report Retest 5");
    }

    private ClinicalDocumentEntity document(String title, LocalDate reportDate) {
        return document(UUID.randomUUID(), UUID.randomUUID(), title, reportDate);
    }

    private ClinicalDocumentEntity document(UUID tenantId, UUID patientId, String title, LocalDate reportDate) {
        ClinicalDocumentEntity entity = ClinicalDocumentEntity.create(
                UUID.randomUUID(),
                tenantId,
                patientId,
                null,
                null,
                UUID.randomUUID(),
                ClinicalDocumentType.LAB_REPORT,
                title,
                "notes",
                reportDate,
                "Uploader",
                "RECEPTION",
                "report.pdf",
                "application/pdf",
                100,
                "bucket",
                "storage-key-" + UUID.randomUUID(),
                "checksum",
                "INTERNAL_ONLY",
                "UNVERIFIED",
                "COMPLETED",
                "COMPLETED",
                null,
                null,
                UUID.randomUUID(),
                UUID.randomUUID()
        );
        return entity;
    }

    private static void setField(Object entity, String fieldName, Object value) {
        try {
            Field field = entity.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(entity, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static PatientLongitudinalConceptEntity pendingConcept(UUID tenantId,
                                                                   UUID patientId,
                                                                   ClinicalDocumentEntity document,
                                                                   String family,
                                                                   String key,
                                                                   String label,
                                                                   String valueText,
                                                                   String unit,
                                                                   String evidenceText,
                                                                   String sourceSummary) {
        return PatientLongitudinalConceptEntity.create(
                tenantId,
                patientId,
                document.getId(),
                document.getDocumentType().name(),
                document.getTitle(),
                document.getReportDate(),
                family,
                key,
                label,
                valueText,
                unit,
                evidenceText,
                sourceSummary,
                "PENDING_REVIEW",
                new BigDecimal("0.91"),
                document.getReportDate().atStartOfDay().atOffset(ZoneOffset.UTC)
        );
    }
}
