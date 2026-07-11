package com.deepthoughtnet.clinic.api.ai.reasoning;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.ai.dto.AiDraftResponse;
import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.ClinicalReasoningResult;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.beans.factory.annotation.Autowired;

@JsonTest
class ClinicalReasoningResponseParserTest {
    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Test
    void parsesValidJsonSafely() {
        ClinicalReasoningResponseParser parser = new ClinicalReasoningResponseParser(objectMapper);
        UUID consultationId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        ClinicalContextResponse context = sampleContext(consultationId, patientId);
        AiDraftResponse response = new AiDraftResponse(
                true,
                false,
                "ok",
                "MOCK",
                "mock-model",
                "{\"confidence\":\"HIGH\",\"primaryDiagnosis\":{\"name\":\"Viral Upper Respiratory Infection\",\"confidence\":0.82,\"status\":\"SUGGESTED\",\"whyConsidered\":\"Fever and cough\",\"whyLessLikely\":\"No chest pain or dyspnea\"},\"differentialDiagnoses\":[{\"name\":\"Influenza\",\"confidence\":0.45,\"whyConsidered\":\"Viral syndrome\",\"whyLessLikely\":\"No influenza-specific features\"}],\"supportingEvidence\":[{\"text\":\"Fever and cough\"}],\"contradictingEvidence\":[],\"missingInformation\":[{\"name\":\"Exposure history\"}],\"redFlags\":[],\"recommendedTests\":[{\"name\":\"COVID/Flu test\"}],\"reasoningSummary\":\"Likely viral upper respiratory illness.\",\"safetyNotes\":[{\"message\":\"Monitor blood sugar during fever\"}],\"followUpAdvice\":[\"Hydrate and rest\"],\"patientExplanation\":\"Likely a viral illness.\",\"sourceContextSummary\":{\"chiefComplaint\":\"Fever\"},\"metadata\":{\"promptVersion\":\"clinic.clinical.reasoning.v1\",\"contextVersion\":\"v1\",\"provider\":\"MOCK\",\"model\":\"mock-model\",\"tokens\":{},\"parseStatus\":\"VALID\"}}",
                mapOf(
                        "confidence", "HIGH",
                        "primaryDiagnosis", mapOf(
                                "name", "Viral Upper Respiratory Infection",
                                "confidence", 0.82,
                                "status", "SUGGESTED",
                                "whyConsidered", "Fever and cough",
                                "whyLessLikely", "No chest pain or dyspnea"
                        ),
                        "differentialDiagnoses", List.of(mapOf(
                                "name", "Influenza",
                                "confidence", 0.45,
                                "whyConsidered", "Viral syndrome",
                                "whyLessLikely", "No influenza-specific features"
                        )),
                        "supportingEvidence", List.of(mapOf("text", "Fever and cough")),
                        "contradictingEvidence", List.of(),
                        "missingInformation", List.of(mapOf("name", "Exposure history")),
                        "redFlags", List.of(),
                        "recommendedTests", List.of(mapOf("name", "COVID/Flu test")),
                        "reasoningSummary", "Likely viral upper respiratory illness.",
                        "safetyNotes", List.of(mapOf("message", "Monitor blood sugar during fever")),
                        "followUpAdvice", List.of("Hydrate and rest"),
                        "patientExplanation", "Likely a viral illness.",
                        "sourceContextSummary", mapOf("chiefComplaint", "Fever"),
                        "metadata", mapOf("promptVersion", "clinic.clinical.reasoning.v1", "contextVersion", "v1", "provider", "MOCK", "model", "mock-model", "tokens", mapOf(), "parseStatus", "VALID")
                ),
                BigDecimal.valueOf(0.88),
                List.of(),
                List.of(),
                null
        );

        ClinicalReasoningResult result = parser.parse(consultationId, patientId, context, response, false, "corr-1", "corr-1", 12L);

        assertThat(result.metadata().parseStatus()).isEqualTo("VALID");
        assertThat(result.primaryDiagnosis().name()).isEqualTo("Viral Upper Respiratory Infection");
        assertThat(result.differentialDiagnoses()).extracting("name").contains("Influenza");
        assertThat(result.redFlags()).isEmpty();
        assertThat(result.recommendedTests()).extracting("name").contains("COVID/Flu test");
        assertThat(result.reasoningSummary()).contains("viral upper respiratory illness");
    }

    @Test
    void malformedJsonReturnsSafeInvalidState() {
        ClinicalReasoningResponseParser parser = new ClinicalReasoningResponseParser(objectMapper);
        ClinicalReasoningResult result = parser.parse(UUID.randomUUID(), UUID.randomUUID(), null, new AiDraftResponse(
                true,
                false,
                "ok",
                "MOCK",
                "mock-model",
                "not json at all",
                Map.of("raw", "not json at all"),
                null,
                List.of(),
                List.of(),
                null
        ), false, "corr-2", "corr-2", 9L);

        assertThat(result.metadata().parseStatus()).isEqualTo("FAILED");
        assertThat(result.primaryDiagnosis()).isNull();
        assertThat(result.differentialDiagnoses()).isEmpty();
        assertThat(result.redFlags()).isEmpty();
        assertThat(result.metadata().errorMessage()).contains("did not contain usable clinical reasoning");
    }

    @Test
    void truncatedFinishReasonReturnsSafeTruncatedState() {
        ClinicalReasoningResponseParser parser = new ClinicalReasoningResponseParser(objectMapper);
        ClinicalReasoningResult result = parser.parse(UUID.randomUUID(), UUID.randomUUID(), null, new AiDraftResponse(
                true,
                false,
                "ok",
                "GEMINI",
                "gemini-2.5-flash",
                "{\"confidence\":\"HIGH\",\"primaryDiagnosis\":{\"name\":\"Viral Upper Respiratory Infection\"}",
                Map.of("raw", "{\"confidence\":\"HIGH\""),
                null,
                List.of(),
                List.of(),
                "MAX_TOKENS"
        ), false, "corr-3", "corr-3", 11L);

        assertThat(result.metadata().parseStatus()).isEqualTo("TRUNCATED");
        assertThat(result.metadata().finishReason()).isEqualTo("MAX_TOKENS");
        assertThat(result.metadata().normalizedFinishReason()).isEqualTo("TRUNCATED");
        assertThat(result.metadata().errorMessage()).contains("truncated");
        assertThat(result.primaryDiagnosis()).isNull();
    }

    @Test
    void safetyBlockedResponseReturnsBlockedState() {
        ClinicalReasoningResponseParser parser = new ClinicalReasoningResponseParser(objectMapper);
        ClinicalReasoningResult result = parser.parse(UUID.randomUUID(), UUID.randomUUID(), null, new AiDraftResponse(
                true,
                false,
                "blocked",
                "GEMINI",
                "gemini-2.5-flash",
                "",
                Map.of("raw", ""),
                null,
                List.of(),
                List.of(),
                "content_filter",
                "BLOCKED",
                0,
                "",
                "BLOCKED"
        ), false, "corr-4", "corr-4", 7L);

        assertThat(result.metadata().parseStatus()).isEqualTo("BLOCKED");
        assertThat(result.primaryDiagnosis()).isNull();
        assertThat(result.metadata().errorMessage()).contains("blocked by safety filters");
    }

    @Test
    void prefersRawTextOverDerivedDraftWhenParsing() {
        ClinicalReasoningResponseParser parser = new ClinicalReasoningResponseParser(objectMapper);
        UUID consultationId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        ClinicalContextResponse context = sampleContext(consultationId, patientId);
        String rawJson = "{\"confidence\":\"HIGH\",\"primaryDiagnosis\":{\"name\":\"Viral Upper Respiratory Infection\",\"confidence\":0.82,\"status\":\"SUGGESTED\",\"whyConsidered\":\"Fever and cough\",\"whyLessLikely\":\"No hypoxia\"},\"differentialDiagnoses\":[{\"name\":\"Influenza\",\"confidence\":0.45,\"whyConsidered\":\"Viral syndrome\",\"whyLessLikely\":\"No influenza-specific features\"}],\"reasoningSummary\":\"Likely viral upper respiratory illness.\",\"metadata\":{\"promptVersion\":\"clinic.clinical.reasoning.v1\",\"contextVersion\":\"v1\",\"provider\":\"MOCK\",\"model\":\"mock-model\",\"tokens\":{},\"parseStatus\":\"VALID\"}}";
        AiDraftResponse response = new AiDraftResponse(
                true,
                false,
                "short draft",
                "MOCK",
                "mock-model",
                "short draft",
                Map.of("raw", rawJson),
                null,
                List.of(),
                List.of(),
                "STOP",
                "COMPLETE",
                rawJson.length(),
                rawJson,
                "VALID"
        );

        ClinicalReasoningResult result = parser.parse(consultationId, patientId, context, response, false, "corr-5", "corr-5", 13L);

        assertThat(result.metadata().rawChars()).isEqualTo(rawJson.length());
        assertThat(result.primaryDiagnosis().name()).isEqualTo("Viral Upper Respiratory Infection");
        assertThat(result.differentialDiagnoses()).extracting("name").contains("Influenza");
    }

    private ClinicalContextResponse sampleContext(UUID consultationId, UUID patientId) {
        return new ClinicalContextResponse(
                UUID.randomUUID(),
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
                null,
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
