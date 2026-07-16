package com.deepthoughtnet.clinic.ai.orchestration.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.ai.orchestration.platform.db.AiGuardrailProfileEntity;
import com.deepthoughtnet.clinic.ai.orchestration.platform.db.AiGuardrailProfileRepository;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AiGuardrailServiceImplTest {

    @Test
    void clampsRequestedMaxTokensToGuardrailLimit() {
        AiGuardrailProfileRepository repository = mock(AiGuardrailProfileRepository.class);
        UUID tenantId = UUID.randomUUID();
        when(repository.findByTenantIdAndProfileKey(tenantId, "default"))
                .thenReturn(Optional.of(AiGuardrailProfileEntity.create(
                        tenantId,
                        "default",
                        "Default",
                        "Default profile",
                        true,
                        null,
                        false,
                        false,
                        1536
                )));
        AiGuardrailServiceImpl service = new AiGuardrailServiceImpl(repository, 2048);

        AiOrchestrationRequest request = new AiOrchestrationRequest(
                AiProductCode.CLINIC,
                tenantId,
                UUID.randomUUID(),
                AiTaskType.CLINICAL_REASONING,
                "clinic.clinical.reasoning.v1",
                Map.of("chiefComplaint", "Fever"),
                List.of(),
                4096,
                0.1d,
                "corr-1",
                "clinical_reasoning_generate"
        );

        AiGuardrailService.ExecutionSettings settings = service.resolveExecutionSettings(tenantId, "prompt text", request, null);

        assertThat(settings.requestedMaxTokens()).isEqualTo(4096);
        assertThat(settings.guardrailLimit()).isEqualTo(1536);
        assertThat(settings.effectiveMaxTokens()).isEqualTo(1536);
        assertThat(settings.compactMode()).isFalse();
    }

    @Test
    void clinicalReasoningUsesConfiguredTaskBudget() {
        AiGuardrailProfileRepository repository = mock(AiGuardrailProfileRepository.class);
        UUID tenantId = UUID.randomUUID();
        when(repository.findByTenantIdAndProfileKey(tenantId, "default"))
                .thenReturn(Optional.of(AiGuardrailProfileEntity.create(
                        tenantId,
                        "default",
                        "Default",
                        "Default profile",
                        true,
                        null,
                        false,
                        false,
                        2048
                )));
        AiGuardrailServiceImpl service = new AiGuardrailServiceImpl(repository, 2048);

        AiOrchestrationRequest request = new AiOrchestrationRequest(
                AiProductCode.CLINIC,
                tenantId,
                UUID.randomUUID(),
                AiTaskType.CLINICAL_REASONING,
                "clinic.clinical.reasoning.v1",
                Map.of("chiefComplaint", "Fever"),
                List.of(),
                null,
                0.1d,
                "corr-1",
                "clinical_reasoning_generate"
        );

        AiGuardrailService.ExecutionSettings settings = service.resolveExecutionSettings(tenantId, "prompt text", request, null);

        assertThat(settings.guardrailLimit()).isEqualTo(2048);
        assertThat(settings.effectiveMaxTokens()).isEqualTo(2048);
        assertThat(settings.compactMode()).isFalse();
    }

    @Test
    void repairModeKeepsConfiguredClinicalReasoningBudget() {
        AiGuardrailProfileRepository repository = mock(AiGuardrailProfileRepository.class);
        UUID tenantId = UUID.randomUUID();
        when(repository.findByTenantIdAndProfileKey(tenantId, "default"))
                .thenReturn(Optional.of(AiGuardrailProfileEntity.create(
                        tenantId,
                        "default",
                        "Default",
                        "Default profile",
                        true,
                        null,
                        false,
                        false,
                        2048
                )));
        AiGuardrailServiceImpl service = new AiGuardrailServiceImpl(repository, 2048);
        String prompt = "x".repeat(5000);
        AiOrchestrationRequest request = new AiOrchestrationRequest(
                AiProductCode.CLINIC,
                tenantId,
                UUID.randomUUID(),
                AiTaskType.CLINICAL_REASONING,
                "clinic.clinical.reasoning.v1",
                Map.of(),
                List.of(),
                null,
                0.1d,
                "corr-2",
                "clinical_reasoning_generate_repair"
        );

        AiGuardrailService.ExecutionSettings settings = service.resolveExecutionSettings(tenantId, prompt, request, null);

        assertThat(settings.compactMode()).isTrue();
        assertThat(settings.effectiveMaxTokens()).isEqualTo(2048);
    }

    @Test
    void consultationSoapKeepsRequestedOutputBudgetAndAvoidsCompactHalving() {
        AiGuardrailProfileRepository repository = mock(AiGuardrailProfileRepository.class);
        UUID tenantId = UUID.randomUUID();
        when(repository.findByTenantIdAndProfileKey(tenantId, "default"))
                .thenReturn(Optional.of(AiGuardrailProfileEntity.create(
                        tenantId,
                        "default",
                        "Default",
                        "Default profile",
                        true,
                        null,
                        false,
                        false,
                        2048
                )));
        AiGuardrailServiceImpl service = new AiGuardrailServiceImpl(repository, 2048);

        AiOrchestrationRequest request = new AiOrchestrationRequest(
                AiProductCode.CLINIC,
                tenantId,
                UUID.randomUUID(),
                AiTaskType.CONSULTATION_NOTE_STRUCTURING,
                "clinic.consultation.structure-notes.v1",
                Map.of("chiefComplaint", "Fever"),
                List.of(),
                4096,
                0.1d,
                "corr-3",
                "consultation_structure_notes"
        );

        AiGuardrailService.ExecutionSettings settings = service.resolveExecutionSettings(tenantId, "prompt text", request, null);

        assertThat(settings.guardrailLimit()).isEqualTo(4096);
        assertThat(settings.effectiveMaxTokens()).isEqualTo(4096);
        assertThat(settings.compactMode()).isFalse();
    }
}
