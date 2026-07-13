package com.deepthoughtnet.clinic.api.ai.reasoning;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.ai.reasoning.dto.ClinicalReasoningRequest;
import com.deepthoughtnet.clinic.consultation.db.ConsultationEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClinicalReasoningContextHasherTest {
    @Test
    void producesStableHashForIdenticalClinicalInputs() {
        ClinicalReasoningPromptBuilder promptBuilder = new ClinicalReasoningPromptBuilder();
        ClinicalReasoningContextHasher hasher = new ClinicalReasoningContextHasher(promptBuilder, new ObjectMapper());

        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        ConsultationEntity consultation = ConsultationEntity.create(tenantId, patientId, UUID.randomUUID(), null);
        consultation.update("Fever", "Fever and cough", null, "CBC normal", null, null, null, null, null, null, null, null, null, null, null);
        ClinicalReasoningRequest request = new ClinicalReasoningRequest(patientId, "Fever", "Fever and cough", "CBC normal", "BP 136/86", null, null, null, null, null);

        String first = hasher.contextHash(tenantId, consultation, null, request);
        String second = hasher.contextHash(tenantId, consultation, null, request);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void changesWhenClinicallyRelevantConsultationInputsChange() {
        ClinicalReasoningPromptBuilder promptBuilder = new ClinicalReasoningPromptBuilder();
        ClinicalReasoningContextHasher hasher = new ClinicalReasoningContextHasher(promptBuilder, new ObjectMapper());

        UUID tenantId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        ConsultationEntity baseline = ConsultationEntity.create(tenantId, patientId, UUID.randomUUID(), null);
        baseline.update("Fever", "Fever and cough", null, "CBC normal", null, null, null, null, null, null, null, null, null, null, null);
        ClinicalReasoningRequest request = new ClinicalReasoningRequest(patientId, "Fever", "Fever and cough", "CBC normal", "BP 136/86", null, null, null, null, null);

        String baselineHash = hasher.contextHash(tenantId, baseline, null, request);
        ConsultationEntity changedComplaint = ConsultationEntity.create(tenantId, patientId, UUID.randomUUID(), null);
        changedComplaint.update("Fever with weakness", "Fever and cough", null, "CBC normal", null, null, null, null, null, null, null, null, null, null, null);
        ConsultationEntity changedSymptoms = ConsultationEntity.create(tenantId, patientId, UUID.randomUUID(), null);
        changedSymptoms.update("Fever", "Fever and cough with shortness of breath", null, "CBC normal", null, null, null, null, null, null, null, null, null, null, null);
        ConsultationEntity changedDiagnosis = ConsultationEntity.create(tenantId, patientId, UUID.randomUUID(), null);
        changedDiagnosis.update("Fever", "Fever and cough", "Lower Respiratory Tract Infection", "CBC normal", null, null, null, null, null, null, null, null, null, null, null);

        assertThat(baselineHash).isNotEqualTo(hasher.contextHash(tenantId, changedComplaint, null, request));
        assertThat(baselineHash).isNotEqualTo(hasher.contextHash(tenantId, changedSymptoms, null, request));
        assertThat(baselineHash).isNotEqualTo(hasher.contextHash(tenantId, changedDiagnosis, null, request));
    }
}
