package com.deepthoughtnet.clinic.api.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.ai.dto.AiDraftResponse;
import com.deepthoughtnet.clinic.ai.orchestration.service.AiOrchestrationService;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTokenUsage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;

class AiDoctorCopilotServiceTest {
    @Test
    void consultationAskUsesBoundedFreeformChatConfiguration() {
        AiOrchestrationService orchestrationService = mock(AiOrchestrationService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicReference<AiOrchestrationRequest> captured = new AtomicReference<>();
        when(orchestrationService.complete(any(AiOrchestrationRequest.class))).thenAnswer(invocation -> {
            AiOrchestrationRequest request = invocation.getArgument(0);
            captured.set(request);
            return new AiOrchestrationResponse(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    AiProductCode.CLINIC,
                    request.taskType(),
                    "GEMINI",
                    "gemini-2.5-flash",
                    "Concise answer with trends.",
                    "{\"answer\":\"Concise answer with trends.\"}",
                    BigDecimal.valueOf(0.92),
                    List.of(),
                    List.of("Review clinically"),
                    List.of("Advisory only"),
                    new AiTokenUsage(780L, 140L, 920L, BigDecimal.valueOf(0.02)),
                    321L,
                    false,
                    null,
                    "STOP",
                    "COMPLETE",
                    28,
                    "Concise answer with trends.",
                    "VALID"
            );
        });

        AiDoctorCopilotService service = new AiDoctorCopilotService(orchestrationService, objectMapper, true);
        RequestContextHolder.set(new RequestContext(TenantId.of(UUID.randomUUID()), UUID.randomUUID(), "sub", java.util.Set.of(), "DOCTOR", "corr"));
        try {
            AiDraftResponse response = service.draft(
                    AiTaskType.GENERIC_COPILOT,
                    "clinic.consultation.ask.v1",
                    "consultation.ask",
                    Map.of("prompt", "Summarize the history", "aiPromptContext", "Patient snapshot"),
                    List.of()
            );

            assertThat(response.draft()).contains("Concise answer");
            assertThat(captured.get()).isNotNull();
            assertThat(captured.get().taskType()).isEqualTo(AiTaskType.GENERIC_COPILOT);
            assertThat(captured.get().promptTemplateCode()).isEqualTo("clinic.consultation.ask.v1");
            assertThat(captured.get().useCaseCode()).isEqualTo("consultation.ask");
            assertThat(captured.get().maxTokens()).isEqualTo(1024);
            assertThat(captured.get().temperature()).isEqualTo(0.1d);
        } finally {
            RequestContextHolder.clear();
        }
    }
}
