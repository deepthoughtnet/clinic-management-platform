package com.deepthoughtnet.clinic.api.ai.reasoning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.ai.dto.AiDraftResponse;
import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.ClinicalReasoningRequest;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.ClinicalReasoningResult;
import com.deepthoughtnet.clinic.api.ai.service.AiDoctorCopilotService;
import com.deepthoughtnet.clinic.consultation.db.ConsultationEntity;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClinicalReasoningEngineTest {
    @Test
    void generatesViralUriAndAvoidsUnsupportedEmergencyDiagnoses() {
        AiDoctorCopilotService copilotService = mock(AiDoctorCopilotService.class);
        ClinicalReasoningPromptBuilder promptBuilder = new ClinicalReasoningPromptBuilder();
        ClinicalReasoningResponseParser parser = new ClinicalReasoningResponseParser(new com.fasterxml.jackson.databind.ObjectMapper());
        ClinicalReasoningEngine engine = new ClinicalReasoningEngine(copilotService, promptBuilder, parser);

        UUID tenantId = UUID.randomUUID();
        ConsultationEntity consultation = ConsultationEntity.create(tenantId, UUID.randomUUID(), UUID.randomUUID(), null);
        consultation.update("Fever, cough, body ache and weakness", "Fever and cough for 4 days", null, "CBC normal", null, null, null, null, null, null, null, null, null, null, null);
        ClinicalContextResponse context = sampleContext(tenantId, consultation.getPatientId(), consultation.getId());
        ClinicalReasoningRequest request = new ClinicalReasoningRequest(consultation.getPatientId(), "Fever, cough, body ache and weakness", "Fever and cough for 4 days", "CBC normal", "BP 136/86, Pulse 96, Temp 101.2 F, SpO2 96", null, null, null, null, null);

        when(copilotService.draft(eq(com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType.CLINICAL_REASONING), anyString(), anyString(), anyMap(), eq(List.of())))
                .thenReturn(new AiDraftResponse(
                        true,
                        false,
                        "ok",
                        "MOCK",
                        "mock-model",
                        "{\"confidence\":\"HIGH\",\"primaryDiagnosis\":{\"name\":\"Viral Upper Respiratory Infection\",\"confidence\":0.82,\"status\":\"SUGGESTED\",\"whyConsidered\":\"Fever and cough\",\"whyLessLikely\":\"No chest pain, dyspnea, or hypoxia\"},\"differentialDiagnoses\":[{\"name\":\"Influenza\",\"confidence\":0.45,\"whyConsidered\":\"Viral syndrome\",\"whyLessLikely\":\"No influenza-specific features\"},{\"name\":\"COVID-like viral illness\",\"confidence\":0.4,\"whyConsidered\":\"Fever and cough\",\"whyLessLikely\":\"No hypoxia\"}],\"supportingEvidence\":[{\"text\":\"Known diabetic\"}],\"contradictingEvidence\":[],\"missingInformation\":[{\"name\":\"Exposure history\"}],\"redFlags\":[],\"recommendedTests\":[{\"name\":\"COVID/Flu test\"}],\"reasoningSummary\":\"Likely viral respiratory illness.\",\"safetyNotes\":[{\"message\":\"Monitor blood sugar during fever\"}],\"followUpAdvice\":[\"Hydrate and rest\"],\"patientExplanation\":\"Likely a viral illness.\",\"sourceContextSummary\":{\"chiefComplaint\":\"Fever\"},\"metadata\":{\"promptVersion\":\"clinic.clinical.reasoning.v1\",\"contextVersion\":\"v1\",\"provider\":\"MOCK\",\"model\":\"mock-model\",\"tokens\":{},\"parseStatus\":\"VALID\"}}",
                mapOf(
                                "confidence", "HIGH",
                                "primaryDiagnosis", mapOf("name", "Viral Upper Respiratory Infection", "confidence", 0.82, "status", "SUGGESTED", "whyConsidered", "Fever and cough", "whyLessLikely", "No chest pain, dyspnea, or hypoxia"),
                                "differentialDiagnoses", List.of(
                                        mapOf("name", "Influenza", "confidence", 0.45, "whyConsidered", "Viral syndrome", "whyLessLikely", "No influenza-specific features"),
                                        mapOf("name", "COVID-like viral illness", "confidence", 0.4, "whyConsidered", "Fever and cough", "whyLessLikely", "No hypoxia")
                                ),
                                "supportingEvidence", List.of(mapOf("text", "Known diabetic")),
                                "contradictingEvidence", List.of(),
                                "missingInformation", List.of(mapOf("name", "Exposure history")),
                                "redFlags", List.of(),
                                "recommendedTests", List.of(mapOf("name", "COVID/Flu test")),
                                "reasoningSummary", "Likely viral respiratory illness.",
                                "safetyNotes", List.of(mapOf("message", "Monitor blood sugar during fever")),
                                "followUpAdvice", List.of("Hydrate and rest"),
                                "patientExplanation", "Likely a viral illness.",
                                "sourceContextSummary", mapOf("chiefComplaint", "Fever"),
                                "metadata", mapOf("promptVersion", "clinic.clinical.reasoning.v1", "contextVersion", "v1", "provider", "MOCK", "model", "mock-model", "tokens", mapOf(), "parseStatus", "VALID")
                        ),
                        BigDecimal.valueOf(0.88),
                        List.of(),
                        List.of(),
                        "STOP",
                        "COMPLETE",
                        355,
                        "{\"confidence\":\"HIGH\",\"primaryDiagnosis\":{\"name\":\"Viral Upper Respiratory Infection\",\"confidence\":0.82,\"status\":\"SUGGESTED\",\"whyConsidered\":\"Fever and cough\",\"whyLessLikely\":\"No chest pain, dyspnea, or hypoxia\"},\"differentialDiagnoses\":[{\"name\":\"Influenza\",\"confidence\":0.45,\"whyConsidered\":\"Viral syndrome\",\"whyLessLikely\":\"No influenza-specific features\"},{\"name\":\"COVID-like viral illness\",\"confidence\":0.4,\"whyConsidered\":\"Fever and cough\",\"whyLessLikely\":\"No hypoxia\"}],\"supportingEvidence\":[{\"text\":\"Known diabetic\"}],\"contradictingEvidence\":[],\"missingInformation\":[{\"name\":\"Exposure history\"}],\"redFlags\":[],\"recommendedTests\":[{\"name\":\"COVID/Flu test\"}],\"reasoningSummary\":\"Likely viral respiratory illness.\",\"safetyNotes\":[{\"message\":\"Monitor blood sugar during fever\"}],\"followUpAdvice\":[\"Hydrate and rest\"],\"patientExplanation\":\"Likely a viral illness.\",\"sourceContextSummary\":{\"chiefComplaint\":\"Fever\"},\"metadata\":{\"promptVersion\":\"clinic.clinical.reasoning.v1\",\"contextVersion\":\"v1\",\"provider\":\"MOCK\",\"model\":\"mock-model\",\"tokens\":{},\"parseStatus\":\"VALID\"}}",
                        "VALID"
                ));

        ClinicalReasoningResult result = engine.generate(new ClinicalReasoningEngine.UUIDContext(tenantId, "corr-1", "corr-1"), consultation, request, context);

        assertThat(result.primaryDiagnosis().name()).isEqualTo("Viral Upper Respiratory Infection");
        assertThat(result.differentialDiagnoses()).extracting("name").contains("Influenza", "COVID-like viral illness");
        assertThat(result.differentialDiagnoses()).extracting("name").doesNotContain("Acute Coronary Syndrome", "Pulmonary Embolism", "Aortic Dissection");
        assertThat(result.supportingEvidence()).isNotEmpty();
        assertThat(result.redFlags()).extracting("name").contains("Diabetes with fever", "SpO2 below 94%");
        assertThat(result.safetyNotes()).extracting("message").contains("Monitor blood sugar during fever");
        assertThat(result.recommendedTests()).extracting("name").contains("COVID/Flu test");
    }

    @Test
    void retriesOnceWhenInitialJsonIsMalformed() {
        AiDoctorCopilotService copilotService = mock(AiDoctorCopilotService.class);
        ClinicalReasoningPromptBuilder promptBuilder = new ClinicalReasoningPromptBuilder();
        ClinicalReasoningResponseParser parser = new ClinicalReasoningResponseParser(new com.fasterxml.jackson.databind.ObjectMapper());
        ClinicalReasoningEngine engine = new ClinicalReasoningEngine(copilotService, promptBuilder, parser);

        UUID tenantId = UUID.randomUUID();
        ConsultationEntity consultation = ConsultationEntity.create(tenantId, UUID.randomUUID(), UUID.randomUUID(), null);
        consultation.update("Fever, cough", "Fever and cough", null, "CBC normal", null, null, null, null, null, null, null, null, null, null, null);
        ClinicalContextResponse context = sampleContext(tenantId, consultation.getPatientId(), consultation.getId());
        ClinicalReasoningRequest request = new ClinicalReasoningRequest(consultation.getPatientId(), "Fever, cough", "Fever and cough", "CBC normal", "BP 136/86, Pulse 96, Temp 101.2 F, SpO2 96", null, null, null, null, null);

        when(copilotService.draft(eq(com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType.CLINICAL_REASONING), anyString(), anyString(), anyMap(), eq(List.of())))
                .thenReturn(new AiDraftResponse(true, false, "bad", "MOCK", "mock-model", "not json", Map.of("raw", "not json"), null, List.of(), List.of(), null, "UNKNOWN", 8, "not json", "FAILED"))
                .thenReturn(new AiDraftResponse(
                        true,
                        false,
                        "ok",
                        "MOCK",
                        "mock-model",
                        "{\"confidence\":\"HIGH\",\"primaryDiagnosis\":{\"name\":\"Viral Upper Respiratory Infection\",\"confidence\":0.82,\"status\":\"SUGGESTED\"},\"reasoningSummary\":\"Likely viral respiratory illness.\",\"metadata\":{\"promptVersion\":\"clinic.clinical.reasoning.v1\",\"contextVersion\":\"v1\",\"provider\":\"MOCK\",\"model\":\"mock-model\",\"tokens\":{},\"parseStatus\":\"VALID\"}}",
                mapOf(
                        "confidence", "HIGH",
                        "primaryDiagnosis", mapOf("name", "Viral Upper Respiratory Infection", "confidence", 0.82, "status", "SUGGESTED"),
                        "reasoningSummary", "Likely viral respiratory illness.",
                        "metadata", mapOf("promptVersion", "clinic.clinical.reasoning.v1", "contextVersion", "v1", "provider", "MOCK", "model", "mock-model", "tokens", mapOf(), "parseStatus", "VALID")
                ),
                        BigDecimal.valueOf(0.88),
                        List.of(),
                        List.of(),
                        "STOP",
                        "COMPLETE",
                        170,
                        "{\"confidence\":\"HIGH\",\"primaryDiagnosis\":{\"name\":\"Viral Upper Respiratory Infection\",\"confidence\":0.82,\"status\":\"SUGGESTED\"},\"reasoningSummary\":\"Likely viral respiratory illness.\",\"metadata\":{\"promptVersion\":\"clinic.clinical.reasoning.v1\",\"contextVersion\":\"v1\",\"provider\":\"MOCK\",\"model\":\"mock-model\",\"tokens\":{},\"parseStatus\":\"VALID\"}}",
                        "VALID"
                ));

        ClinicalReasoningResult result = engine.generate(new ClinicalReasoningEngine.UUIDContext(tenantId, "corr-2", "corr-2"), consultation, request, context);

        assertThat(result.primaryDiagnosis().name()).isEqualTo("Viral Upper Respiratory Infection");
        assertThat(result.metadata().parseStatus()).isEqualTo("VALID");
    }

    @Test
    void retriesOnceWhenInitialResponseIsTruncated() {
        AiDoctorCopilotService copilotService = mock(AiDoctorCopilotService.class);
        ClinicalReasoningPromptBuilder promptBuilder = new ClinicalReasoningPromptBuilder();
        ClinicalReasoningResponseParser parser = new ClinicalReasoningResponseParser(new com.fasterxml.jackson.databind.ObjectMapper());
        ClinicalReasoningEngine engine = new ClinicalReasoningEngine(copilotService, promptBuilder, parser);

        UUID tenantId = UUID.randomUUID();
        ConsultationEntity consultation = ConsultationEntity.create(tenantId, UUID.randomUUID(), UUID.randomUUID(), null);
        ClinicalContextResponse context = sampleContext(tenantId, consultation.getPatientId(), consultation.getId());
        ClinicalReasoningRequest request = new ClinicalReasoningRequest(consultation.getPatientId(), "Fever", "Fever", "CBC normal", "BP 136/86", null, null, null, null, null);

        when(copilotService.draft(eq(com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType.CLINICAL_REASONING), anyString(), anyString(), anyMap(), eq(List.of())))
                .thenReturn(new AiDraftResponse(true, false, "bad", "GEMINI", "gemini-2.5-flash", "{\"confidence\":\"HIGH\"", Map.of("raw", "{\"confidence\":\"HIGH\""), null, List.of(), List.of(), "MAX_TOKENS", "TRUNCATED", 20, "{\"confidence\":\"HIGH\"", "TRUNCATED"))
                .thenReturn(new AiDraftResponse(true, false, "ok", "GEMINI", "gemini-2.5-flash",
                        "{\"confidence\":\"HIGH\",\"primaryDiagnosis\":{\"name\":\"Viral Upper Respiratory Infection\",\"confidence\":0.82,\"status\":\"SUGGESTED\"},\"differentialDiagnoses\":[{\"name\":\"Influenza\",\"confidence\":0.45,\"whyConsidered\":\"Viral syndrome\",\"whyLessLikely\":\"No influenza-specific features\"}],\"reasoningSummary\":\"Likely viral respiratory illness.\"}",
                        Map.of(
                                "confidence", "HIGH",
                                "primaryDiagnosis", mapOf("name", "Viral Upper Respiratory Infection", "confidence", 0.82, "status", "SUGGESTED"),
                                "differentialDiagnoses", List.of(mapOf("name", "Influenza", "confidence", 0.45, "whyConsidered", "Viral syndrome", "whyLessLikely", "No influenza-specific features")),
                                "reasoningSummary", "Likely viral respiratory illness."
                        ),
                        BigDecimal.valueOf(0.88),
                        List.of(),
                        List.of(),
                        "STOP",
                        "COMPLETE",
                        319,
                        "{\"confidence\":\"HIGH\",\"primaryDiagnosis\":{\"name\":\"Viral Upper Respiratory Infection\",\"confidence\":0.82,\"status\":\"SUGGESTED\"},\"reasoningSummary\":\"Likely viral respiratory illness.\",\"metadata\":{\"promptVersion\":\"clinic.clinical.reasoning.v1\",\"contextVersion\":\"v1\",\"provider\":\"MOCK\",\"model\":\"mock-model\",\"tokens\":{},\"parseStatus\":\"VALID\"}}",
                        "VALID"
                ));

        ClinicalReasoningResult result = engine.generate(new ClinicalReasoningEngine.UUIDContext(tenantId, "corr-3", "corr-3"), consultation, request, context);

        assertThat(result.metadata().parseStatus()).isEqualTo("VALID");
        assertThat(result.metadata().finishReason()).isEqualTo("STOP");
        assertThat(result.metadata().normalizedFinishReason()).isEqualTo("COMPLETE");
        assertThat(result.primaryDiagnosis().name()).isEqualTo("Viral Upper Respiratory Infection");
    }

    @Test
    void enrichesEmptySectionsFromClinicalContextForDiabeticFebrilePatient() {
        AiDoctorCopilotService copilotService = mock(AiDoctorCopilotService.class);
        ClinicalReasoningPromptBuilder promptBuilder = new ClinicalReasoningPromptBuilder();
        ClinicalReasoningResponseParser parser = new ClinicalReasoningResponseParser(new com.fasterxml.jackson.databind.ObjectMapper());
        ClinicalReasoningEngine engine = new ClinicalReasoningEngine(copilotService, promptBuilder, parser);

        UUID tenantId = UUID.randomUUID();
        ConsultationEntity consultation = ConsultationEntity.create(tenantId, UUID.randomUUID(), UUID.randomUUID(), null);
        consultation.update("Fever, cough, body ache and weakness", "Fever and cough for 4 days", null, "CBC normal", null, null, null, null, null, null, null, null, null, null, null);
        ClinicalContextResponse context = new ClinicalContextResponse(
                tenantId,
                consultation.getPatientId(),
                consultation.getId(),
                new ClinicalContextResponse.PatientSnapshot("Rohan Sharma", 42, "MALE", "Diabetes Mellitus", null, List.of("Metformin"), "2026-01-08"),
                List.of(),
                new ClinicalContextResponse.MedicationSummary(List.of("Metformin"), List.of(), List.of(), List.of(), List.of()),
                new ClinicalContextResponse.DiagnosisSummary("Viral fever", List.of()),
                new ClinicalContextResponse.IntakeSummary(true, "Fever, cough, body ache and weakness",
                        new ClinicalContextResponse.VitalsSnapshot(168.0, 74.0, 27.4, "Overweight", 136, 86, 96, 101.2, "F", 96, 20, 186.0, 8),
                        "BP 136/86 → Pulse 96", List.of("Fever"), "External lab report uploaded", "Patient stable", "Front Desk", "2026-01-08T10:30:00Z"),
                new ClinicalContextResponse.LabIntelligence(
                        "CBC normal",
                        List.of("HbA1c 8.4%", "Random Blood Sugar 198 mg/dL"),
                        List.of(),
                        List.of("CBC"),
                        "8.4%",
                        "CBC normal",
                        null,
                        "198 mg/dL",
                        "High lipids",
                        null,
                        null
                ),
                new ClinicalContextResponse.DocumentIntelligence(List.of("Diabetes Follow-up Lab Report"), List.of(), List.of(), List.of()),
                new ClinicalContextResponse.TimelineSummary(List.of(), "Recent lab report uploaded"),
                new ClinicalContextResponse.LongitudinalMemory(
                        List.of(new ClinicalContextResponse.LongitudinalConcept("CONDITION", "diabetes_mellitus", "Diabetes Mellitus", "Diabetes Mellitus", null, "Diabetes Follow-up Lab Report", "EXTERNAL_LAB_REPORT", UUID.randomUUID().toString(), "2026-01-08", BigDecimal.valueOf(0.96), "PENDING_REVIEW", "Known diabetic")),
                        List.of(new ClinicalContextResponse.LongitudinalConcept("MEDICATION", "metformin", "Metformin", "Metformin", null, "Diabetes Follow-up Lab Report", "EXTERNAL_LAB_REPORT", UUID.randomUUID().toString(), "2026-01-08", BigDecimal.valueOf(0.96), "PENDING_REVIEW", "Long-term medicine")),
                        new ClinicalContextResponse.LongitudinalConcept("LAB_RESULT", "hba1c", "HbA1c", "8.4", "%", "Diabetes Follow-up Lab Report", "EXTERNAL_LAB_REPORT", UUID.randomUUID().toString(), "2026-01-08", BigDecimal.valueOf(0.96), "PENDING_REVIEW", "HbA1c 8.4%"),
                        new ClinicalContextResponse.LongitudinalConcept("LAB_RESULT", "blood_sugar", "Random Blood Sugar", "198", "mg/dL", "Diabetes Follow-up Lab Report", "EXTERNAL_LAB_REPORT", UUID.randomUUID().toString(), "2026-01-08", BigDecimal.valueOf(0.96), "PENDING_REVIEW", "Random Blood Sugar 198 mg/dL"),
                        List.of(
                                new ClinicalContextResponse.LongitudinalConcept("LAB_RESULT", "cholesterol", "Total Cholesterol", "228", "mg/dL", "Diabetes Follow-up Lab Report", "EXTERNAL_LAB_REPORT", UUID.randomUUID().toString(), "2026-01-08", BigDecimal.valueOf(0.96), "PENDING_REVIEW", "Total Cholesterol 228 mg/dL"),
                                new ClinicalContextResponse.LongitudinalConcept("LAB_RESULT", "ldl", "LDL Cholesterol", "152", "mg/dL", "Diabetes Follow-up Lab Report", "EXTERNAL_LAB_REPORT", UUID.randomUUID().toString(), "2026-01-08", BigDecimal.valueOf(0.96), "PENDING_REVIEW", "LDL Cholesterol 152 mg/dL")
                        ),
                        null,
                        null,
                        List.of(new ClinicalContextResponse.LongitudinalConcept("RISK_FLAG", "diabetes", "Diabetes", "Diabetes", null, "Diabetes Follow-up Lab Report", "EXTERNAL_LAB_REPORT", UUID.randomUUID().toString(), "2026-01-08", BigDecimal.valueOf(0.96), "PENDING_REVIEW", "Known diabetic")),
                        List.of(),
                        "Known diabetic with recent report"
                ),
                "Known diabetic with recent report",
                "Patient snapshot",
                "{\"patientSummary\":{\"patientName\":\"Rohan Sharma\"}}",
                OffsetDateTime.now()
        );
        ClinicalReasoningRequest request = new ClinicalReasoningRequest(consultation.getPatientId(), "Fever, cough, body ache and weakness", "Fever and cough for 4 days", "CBC normal", "BP 136/86, Pulse 96, Temp 101.2 F, SpO2 96", null, null, null, null, null);

        String sparseJson = "{\"confidence\":\"HIGH\",\"primaryDiagnosis\":{\"name\":\"Viral Upper Respiratory Infection\",\"confidence\":0.82,\"status\":\"SUGGESTED\"},\"supportingEvidence\":[],\"contradictingEvidence\":[],\"missingInformation\":[],\"redFlags\":[],\"recommendedTests\":[],\"reasoningSummary\":\"Likely viral respiratory illness.\",\"safetyNotes\":[],\"followUpAdvice\":[],\"patientExplanation\":\"Likely a viral illness.\",\"metadata\":{\"promptVersion\":\"clinic.clinical.reasoning.v1\",\"contextVersion\":\"v1\",\"provider\":\"MOCK\",\"model\":\"mock-model\",\"tokens\":{},\"parseStatus\":\"VALID\"}}";
        when(copilotService.draft(eq(com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType.CLINICAL_REASONING), anyString(), anyString(), anyMap(), eq(List.of())))
                .thenReturn(new AiDraftResponse(
                        true,
                        false,
                        "ok",
                        "MOCK",
                        "mock-model",
                        sparseJson,
                        Map.of("raw", sparseJson),
                        BigDecimal.valueOf(0.88),
                        List.of(),
                        List.of(),
                        "STOP"
                ));

        ClinicalReasoningResult result = engine.generate(new ClinicalReasoningEngine.UUIDContext(tenantId, "corr-4", "corr-4"), consultation, request, context);

        assertThat(result.supportingEvidence()).isNotEmpty();
        assertThat(result.missingInformation()).extracting("name").contains("Breathlessness", "Chest pain", "Exposure or travel history");
        assertThat(result.redFlags()).extracting("name").contains("Diabetes with fever", "SpO2 below 94%");
        assertThat(result.recommendedTests()).extracting("actionType").contains("COMPLETE_PENDING_ORDER", "REVIEW_EXISTING_RESULT");
        assertThat(result.safetyNotes()).extracting("message").contains("Monitor glucose more often during fever");
    }

    private ClinicalContextResponse sampleContext(UUID tenantId, UUID patientId, UUID consultationId) {
        return new ClinicalContextResponse(
                tenantId,
                patientId,
                consultationId,
                new ClinicalContextResponse.PatientSnapshot("Rohan Sharma", 42, "MALE", "Diabetes Mellitus", null, List.of("Metformin"), "2026-01-08"),
                List.of(),
                new ClinicalContextResponse.MedicationSummary(List.of("Metformin"), List.of(), List.of(), List.of(), List.of()),
                new ClinicalContextResponse.DiagnosisSummary("Viral fever", List.of()),
                new ClinicalContextResponse.IntakeSummary(true, "Fever and cough", null, null, List.of(), null, null, null, null),
                new ClinicalContextResponse.LabIntelligence("CBC normal", List.of(), List.of(), List.of(), "8.4%", null, null, "198 mg/dL", "High lipids", null, null),
                new ClinicalContextResponse.DocumentIntelligence(List.of("Diabetes Follow-up Lab Report"), List.of(), List.of(), List.of()),
                new ClinicalContextResponse.TimelineSummary(List.of(), "Recent lab report uploaded"),
                new ClinicalContextResponse.LongitudinalMemory(List.of(), List.of(), null, null, List.of(), null, null, List.of(), List.of(), "Known diabetic"),
                "Known diabetic with recent report",
                "Patient snapshot",
                "{\"patientSummary\":{\"patientName\":\"Rohan Sharma\"}}",
                OffsetDateTime.now()
        );
    }

    private static Map<String, Object> mapOf(Object... entries) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            map.put((String) entries[i], entries[i + 1]);
        }
        return map;
    }
}
