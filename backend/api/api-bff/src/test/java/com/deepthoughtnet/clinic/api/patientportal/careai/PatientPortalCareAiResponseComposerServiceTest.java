package com.deepthoughtnet.clinic.api.patientportal.careai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.ai.orchestration.service.AiOrchestrationService;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PatientPortalCareAiResponseComposerServiceTest {

    @Test
    void rewritesSlotListProfessionally() {
        AiOrchestrationService ai = org.mockito.Mockito.mock(AiOrchestrationService.class);
        AivaResponseComposerProperties properties = new AivaResponseComposerProperties();
        PatientPortalCareAiResponseComposerService service = new PatientPortalCareAiResponseComposerService(ai, new ObjectMapper(), properties);
        when(ai.complete(any(AiOrchestrationRequest.class))).thenReturn(response(
                "डॉक्टर आशीष श्री के लिए ये स्लॉट उपलब्ध हैं। पहला, सुबह नौ बजे। दूसरा, सुबह साढ़े नौ बजे। तीसरा, सुबह दस बजे। कृपया इनमें से एक स्लॉट चुनिए।"
        ));

        String polished = service.compose(
                "Please choose a slot by number or time: 1. 09:00 2. 09:30 3. 10:00",
                "slot_list",
                "hi-IN",
                "BOOK_APPOINTMENT",
                service.safeStructuredFacts(
                        "Dr Ashish Shri",
                        null,
                        "2026-06-29",
                        null,
                        List.of("09:00", "09:30", "10:00"),
                        List.of(),
                        false,
                        "BOOK_APPOINTMENT"
                )
        );

        assertThat(polished).contains("डॉक्टर आशीष श्री");
        assertThat(polished).contains("सुबह साढ़े नौ बजे");
        verify(ai).complete(argThat(request -> request.productCode() == AiProductCode.GENERIC
                && request.taskType() == AiTaskType.GENERIC_COPILOT
                && "patient.portal.careai.response.composer.v1".equals(request.promptTemplateCode())
                && request.inputVariables() != null
                && request.inputVariables().containsKey("safeStructuredFactsJson")));
    }

    @Test
    void confirmationPromptKeepsDoctorDateAndTimeUnchanged() {
        AiOrchestrationService ai = org.mockito.Mockito.mock(AiOrchestrationService.class);
        AivaResponseComposerProperties properties = new AivaResponseComposerProperties();
        PatientPortalCareAiResponseComposerService service = new PatientPortalCareAiResponseComposerService(ai, new ObjectMapper(), properties);
        when(ai.complete(any(AiOrchestrationRequest.class))).thenReturn(response(
                "डॉक्टर आशीष श्री के लिए उनतीस जून, सुबह साढ़े नौ बजे का स्लॉट उपलब्ध है। क्या मैं यह अपॉइंटमेंट बुक कर दूँ?"
        ));

        String polished = service.compose(
                "Should I book Dr Ashish Shri on 2026-06-29 at 09:30?",
                "confirmation_prompt",
                "hi-IN",
                "BOOK_APPOINTMENT",
                service.safeStructuredFacts(
                        "Dr Ashish Shri",
                        null,
                        "2026-06-29",
                        "09:30",
                        List.of(),
                        List.of(),
                        true,
                        "BOOK_APPOINTMENT"
                )
        );

        assertThat(polished).contains("डॉक्टर आशीष श्री");
        assertThat(polished).contains("उनतीस जून");
        assertThat(polished).contains("सुबह साढ़े नौ बजे");
    }

    @Test
    void bookedSuccessIsPolished() {
        AiOrchestrationService ai = org.mockito.Mockito.mock(AiOrchestrationService.class);
        AivaResponseComposerProperties properties = new AivaResponseComposerProperties();
        PatientPortalCareAiResponseComposerService service = new PatientPortalCareAiResponseComposerService(ai, new ObjectMapper(), properties);
        when(ai.complete(any(AiOrchestrationRequest.class))).thenReturn(response("आपकी अपॉइंटमेंट सफलतापूर्वक बुक हो गई है।"));

        String polished = service.compose(
                "Appointment booked successfully.",
                "booked_success",
                "hi-IN",
                "BOOK_APPOINTMENT",
                service.safeStructuredFacts(
                        "Dr Ashish Shri",
                        "Sunrise Clinic",
                        "2026-06-29",
                        "09:30",
                        List.of(),
                        List.of(),
                        false,
                        "BOOK_APPOINTMENT"
                )
        );

        assertThat(polished).isEqualTo("आपकी अपॉइंटमेंट सफलतापूर्वक बुक हो गई है।");
    }

    @Test
    void llmFailureFallsBackToRawText() {
        AiOrchestrationService ai = org.mockito.Mockito.mock(AiOrchestrationService.class);
        AivaResponseComposerProperties properties = new AivaResponseComposerProperties();
        PatientPortalCareAiResponseComposerService service = new PatientPortalCareAiResponseComposerService(ai, new ObjectMapper(), properties);
        when(ai.complete(any(AiOrchestrationRequest.class))).thenThrow(new IllegalStateException("LLM unavailable"));

        String raw = "Please choose a slot by number or time: 1. 09:00 2. 09:30 3. 10:00";
        String polished = service.compose(raw, "slot_list", "hi-IN", "BOOK_APPOINTMENT", service.safeStructuredFacts(null, null, null, null, List.of(), List.of(), false, null));

        assertThat(polished).isEqualTo(raw);
    }

    @Test
    void composerDisabledReturnsRawText() {
        AiOrchestrationService ai = org.mockito.Mockito.mock(AiOrchestrationService.class);
        AivaResponseComposerProperties properties = new AivaResponseComposerProperties();
        properties.setEnabled(false);
        PatientPortalCareAiResponseComposerService service = new PatientPortalCareAiResponseComposerService(ai, new ObjectMapper(), properties);

        String raw = "Appointment booked successfully.";
        String polished = service.compose(raw, "booked_success", "en", "BOOK_APPOINTMENT", service.safeStructuredFacts(null, null, null, null, List.of(), List.of(), false, null));

        assertThat(polished).isEqualTo(raw);
        verify(ai, never()).complete(any());
    }

    private AiOrchestrationResponse response(String outputText) {
        return new AiOrchestrationResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                AiProductCode.GENERIC,
                AiTaskType.GENERIC_COPILOT,
                "GEMINI",
                "gemini-1.5-flash",
                outputText,
                null,
                BigDecimal.valueOf(0.9),
                List.of(),
                List.of(),
                List.of(),
                null,
                100L,
                false,
                null
        );
    }
}
