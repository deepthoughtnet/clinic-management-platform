package com.deepthoughtnet.clinic.api.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.ai.dto.AiConsultationAskRequest;
import com.deepthoughtnet.clinic.api.ai.dto.AiDraftResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiConsultationAskServiceTest {
    @Test
    void askUsesGenericCopilotAndForwardsPromptContext() {
        AiDoctorCopilotService copilotService = mock(AiDoctorCopilotService.class);
        when(copilotService.draft(any(), anyString(), anyString(), any(), any())).thenReturn(new AiDraftResponse(
                true,
                false,
                "Answer",
                "MOCK",
                "mock-model",
                "{\"answer\":\"Check hydration and fever\"}",
                Map.of("answer", "Check hydration and fever"),
                BigDecimal.valueOf(0.9),
                List.of("Review"),
                List.of("Advisory")
        ));

        AiConsultationAskService service = new AiConsultationAskService(copilotService);
        AiConsultationAskRequest request = new AiConsultationAskRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "What should I watch for?",
                "30Y / FEMALE",
                "Vitals stable",
                "None",
                "Hypertension",
                "Paracetamol 500 mg",
                "CBC ordered",
                "Fever",
                "Cough",
                "Vitals stable",
                "Viral syndrome",
                "Supportive care"
        );

        AiDraftResponse response = service.ask(request);

        assertThat(response.draft()).contains("Check hydration");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> inputCaptor = ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
        verify(copilotService).draft(
                eq(AiTaskType.GENERIC_COPILOT),
                eq("generic.copilot.v1"),
                eq("consultation.ask"),
                inputCaptor.capture(),
                eq(List.of())
        );
        Map<String, Object> input = inputCaptor.getValue();
        assertThat(input.get("prompt")).isEqualTo("What should I watch for?");
        assertThat(input.get("patientAgeGender")).isEqualTo("30Y / FEMALE");
        assertThat(input.get("currentPrescriptionDraft")).isEqualTo("Paracetamol 500 mg");
        assertThat(input.get("chiefComplaints")).isEqualTo("Fever");
        assertThat(input.get("diagnosis")).isEqualTo("Viral syndrome");
    }
}
