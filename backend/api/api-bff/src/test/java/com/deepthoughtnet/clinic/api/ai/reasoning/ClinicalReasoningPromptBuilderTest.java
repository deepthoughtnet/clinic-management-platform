package com.deepthoughtnet.clinic.api.ai.reasoning;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.ClinicalReasoningRequest;
import com.deepthoughtnet.clinic.consultation.db.ConsultationEntity;
import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClinicalReasoningPromptBuilderTest {

    @Test
    void buildInputIncludesClinicalContextAndStrictInstructions() {
        ClinicalReasoningPromptBuilder builder = new ClinicalReasoningPromptBuilder();
        ConsultationEntity consultation = ConsultationEntity.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null);
        consultation.update("Fever, cough and body ache", "Fever and cough", null, "CBC normal", null, null, null, null, null, null, null, null, null, null, null);

        ClinicalContextResponse context = new ClinicalContextResponse(
                UUID.randomUUID(),
                consultation.getPatientId(),
                consultation.getId(),
                new ClinicalContextResponse.PatientSnapshot("Rohan Sharma", 42, PatientGender.MALE.name(), "Diabetes Mellitus", null, List.of("Metformin"), "2026-01-08"),
                List.of(),
                new ClinicalContextResponse.MedicationSummary(List.of("Metformin"), List.of(), List.of(), List.of(), List.of()),
                new ClinicalContextResponse.DiagnosisSummary("Viral fever", List.of()),
                new ClinicalContextResponse.IntakeSummary(true, "Fever and cough", new ClinicalContextResponse.VitalsSnapshot(168.0, 74.0, 26.2, "Normal weight", 136, 86, 96, 101.2, "F", 96, 20, 186.0, 8), "BP 136/86 → Pulse 96", List.of("Fever"), "External lab report uploaded", "Patient stable", "Front Desk", "2026-01-08T10:30:00Z"),
                new ClinicalContextResponse.LabIntelligence("CBC normal", List.of(), List.of(), List.of(), "8.4%", null, null, "198 mg/dL", "High lipids", null, null),
                new ClinicalContextResponse.DocumentIntelligence(List.of("Diabetes Follow-up Lab Report"), List.of(), List.of(), List.of()),
                new ClinicalContextResponse.TimelineSummary(List.of(), "Recent lab report uploaded"),
                new ClinicalContextResponse.LongitudinalMemory(
                        List.of(new ClinicalContextResponse.LongitudinalConcept("CONDITION", "diabetes_mellitus", "Diabetes Mellitus", "Diabetes Mellitus", null, "Diabetes Follow-up Lab Report", "EXTERNAL_LAB_REPORT", UUID.randomUUID().toString(), "2026-01-08", BigDecimal.valueOf(0.96), "PENDING_REVIEW", "Known diabetic")),
                        List.of(new ClinicalContextResponse.LongitudinalConcept("MEDICATION", "metformin", "Metformin", "Metformin", null, "Diabetes Follow-up Lab Report", "EXTERNAL_LAB_REPORT", UUID.randomUUID().toString(), "2026-01-08", BigDecimal.valueOf(0.96), "PENDING_REVIEW", "Long-term medicine")),
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        List.of(),
                        List.of(),
                        "Known diabetic, HbA1c 8.4%"
                ),
                "Known diabetic with recent report",
                "Patient snapshot",
                "{\"patientSummary\":{\"patientName\":\"Rohan Sharma\"}}",
                OffsetDateTime.now()
        );

        ClinicalReasoningRequest request = new ClinicalReasoningRequest(
                consultation.getPatientId(),
                "Fever, cough and body ache",
                "Fever and cough",
                "CBC normal",
                null,
                null,
                null,
                null,
                null,
                null
        );

        var input = builder.buildInput(context.tenantId(), consultation, context, request, false, null);

        assertThat(input.get("reasoningPrompt").toString()).contains("Return strict JSON only");
        assertThat(input.get("reasoningPrompt").toString()).contains("avoid unsupported ACS, PE, or aortic dissection");
        assertThat(input.get("reasoningPrompt").toString()).contains("max 3 differential diagnoses");
        assertThat(input.get("reasoningPrompt").toString()).contains("reasoningSummary max 300 chars");
        assertThat(input.get("reasoningPrompt").toString()).contains("Pending lab orders");
        assertThat(input.get("reasoningPrompt").toString()).contains("Available labs");
        assertThat(input.get("reasoningPrompt").toString()).contains("Always populate supportingEvidence");
        assertThat(input.get("reasoningPrompt").toString()).contains("For fever with diabetes");
        assertThat(input.get("reasoningPrompt").toString()).doesNotContain("\"patientId\"");
        assertThat(input.get("chiefComplaint")).isEqualTo("Fever, cough and body ache");
        assertThat(input.get("symptoms")).isEqualTo("Fever and cough");
        String vitals = String.valueOf(input.get("vitals"));
        assertThat(vitals).contains("INTAKE").contains("BP 136/86");
        assertThat((List<String>) input.get("knownConditions")).contains("Diabetes Mellitus");
        assertThat((List<String>) input.get("recentReports")).contains("Diabetes Follow-up Lab Report");
    }

    @Test
    void repairModeUsesSmallerPromptThanPrimaryMode() {
        ClinicalReasoningPromptBuilder builder = new ClinicalReasoningPromptBuilder();
        ConsultationEntity consultation = ConsultationEntity.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null);
        consultation.update("Fever, cough and body ache", "Fever and cough", null, "CBC normal", null, null, null, null, null, null, null, null, null, null, null);
        ClinicalContextResponse context = new ClinicalContextResponse(
                UUID.randomUUID(),
                consultation.getPatientId(),
                consultation.getId(),
                new ClinicalContextResponse.PatientSnapshot("Rohan Sharma", 42, PatientGender.MALE.name(), "Diabetes Mellitus", null, List.of("Metformin"), "2026-01-08"),
                List.of(),
                new ClinicalContextResponse.MedicationSummary(List.of("Metformin"), List.of(), List.of(), List.of(), List.of()),
                new ClinicalContextResponse.DiagnosisSummary("Viral fever", List.of()),
                new ClinicalContextResponse.IntakeSummary(true, "Fever and cough", new ClinicalContextResponse.VitalsSnapshot(168.0, 74.0, 26.2, "Normal weight", 136, 86, 96, 101.2, "F", 96, 20, 186.0, 8), "BP 136/86 → Pulse 96", List.of("Fever"), "External lab report uploaded", "Patient stable", "Front Desk", "2026-01-08T10:30:00Z"),
                new ClinicalContextResponse.LabIntelligence("CBC normal", List.of(), List.of(), List.of(), "8.4%", null, null, "198 mg/dL", "High lipids", null, null),
                new ClinicalContextResponse.DocumentIntelligence(List.of("Diabetes Follow-up Lab Report"), List.of(), List.of(), List.of()),
                new ClinicalContextResponse.TimelineSummary(List.of(), "Recent lab report uploaded"),
                new ClinicalContextResponse.LongitudinalMemory(List.of(), List.of(), null, null, List.of(), null, null, List.of(), List.of(), "Known diabetic, HbA1c 8.4%"),
                "Known diabetic with recent report",
                "Patient snapshot",
                "{\"patientSummary\":{\"patientName\":\"Rohan Sharma\"}}",
                OffsetDateTime.now()
        );
        ClinicalReasoningRequest request = new ClinicalReasoningRequest(
                consultation.getPatientId(),
                "Fever, cough and body ache",
                "Fever and cough",
                "CBC normal",
                null,
                null,
                null,
                null,
                null,
                null
        );

        String primary = builder.buildInput(context.tenantId(), consultation, context, request, false, null).get("reasoningPrompt").toString();
        String repair = builder.buildInput(context.tenantId(), consultation, context, request, true, "truncated").get("reasoningPrompt").toString();

        assertThat(repair.length()).isLessThan(primary.length());
        assertThat(repair).contains("Return valid JSON only");
        assertThat(repair).contains("max 2 differential diagnoses");
        assertThat(repair).doesNotContain("No diagnosis without clinical justification");
    }
}
