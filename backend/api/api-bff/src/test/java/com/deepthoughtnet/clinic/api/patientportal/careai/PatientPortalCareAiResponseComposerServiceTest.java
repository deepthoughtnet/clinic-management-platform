package com.deepthoughtnet.clinic.api.patientportal.careai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.ai.orchestration.service.AiProviderRouter;
import com.deepthoughtnet.clinic.llm.spi.AiProviderException;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProvider;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PatientPortalCareAiResponseComposerServiceTest {

    @Test
    void plainHindiTextResponseIsReturnedAsIs() {
        AiProviderRouter router = org.mockito.Mockito.mock(AiProviderRouter.class);
        AiProvider provider = org.mockito.Mockito.mock(AiProvider.class);
        AivaResponseComposerProperties properties = new AivaResponseComposerProperties();
        properties.setEnabled(true);
        PatientPortalCareAiResponseComposerService service = new PatientPortalCareAiResponseComposerService(router, new ObjectMapper(), properties);
        when(router.resolveCandidates(AiTaskType.GENERIC_COPILOT)).thenReturn(List.of(provider));
        when(provider.providerName()).thenReturn("GEMINI");
        when(provider.complete(any(AiProviderRequest.class))).thenReturn(response(
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
        verify(router).resolveCandidates(AiTaskType.GENERIC_COPILOT);
        verify(provider).complete(argThat(request -> request.request().productCode() == AiProductCode.GENERIC
                && request.request().taskType() == AiTaskType.GENERIC_COPILOT
                && request.systemPrompt().contains("professional, warm clinic assistant")
                && request.userPrompt().contains("rawResponseText:")));
    }

    @Test
    void quotedPlainTextResponseIsUnwrapped() {
        AiProviderRouter router = org.mockito.Mockito.mock(AiProviderRouter.class);
        AiProvider provider = org.mockito.Mockito.mock(AiProvider.class);
        AivaResponseComposerProperties properties = new AivaResponseComposerProperties();
        properties.setEnabled(true);
        PatientPortalCareAiResponseComposerService service = new PatientPortalCareAiResponseComposerService(router, new ObjectMapper(), properties);
        when(router.resolveCandidates(AiTaskType.GENERIC_COPILOT)).thenReturn(List.of(provider));
        when(provider.providerName()).thenReturn("GEMINI");
        when(provider.complete(any(AiProviderRequest.class))).thenReturn(response(
                "\"डॉक्टर आशीष श्री के लिए उनतीस जून, सुबह साढ़े नौ बजे का स्लॉट उपलब्ध है। क्या मैं यह अपॉइंटमेंट बुक कर दूँ?\""
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

        assertThat(polished).isEqualTo("डॉक्टर आशीष श्री के लिए उनतीस जून, सुबह साढ़े नौ बजे का स्लॉट उपलब्ध है। क्या मैं यह अपॉइंटमेंट बुक कर दूँ?");
    }

    @Test
    void invalidOrPartialResponseFallsBackToRawText() {
        AiProviderRouter router = org.mockito.Mockito.mock(AiProviderRouter.class);
        AiProvider provider = org.mockito.Mockito.mock(AiProvider.class);
        AivaResponseComposerProperties properties = new AivaResponseComposerProperties();
        properties.setEnabled(true);
        PatientPortalCareAiResponseComposerService service = new PatientPortalCareAiResponseComposerService(router, new ObjectMapper(), properties);
        when(router.resolveCandidates(AiTaskType.GENERIC_COPILOT)).thenReturn(List.of(provider));
        when(provider.providerName()).thenReturn("GEMINI");
        when(provider.complete(any(AiProviderRequest.class))).thenReturn(response("\"क्या"));

        String raw = "Please choose a slot by number or time: 1. 09:00 2. 09:30 3. 10:00";
        String polished = service.compose(
                raw,
                "slot_list",
                "hi-IN",
                "BOOK_APPOINTMENT",
                service.safeStructuredFacts(
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        List.of(),
                        false,
                        null
                )
        );

        assertThat(polished).isEqualTo(raw);
    }

    @Test
    void geminiQuotaFallbackToGroqReturnsCleanPlainText() {
        AiProviderRouter router = org.mockito.Mockito.mock(AiProviderRouter.class);
        AiProvider gemini = org.mockito.Mockito.mock(AiProvider.class);
        AiProvider groq = org.mockito.Mockito.mock(AiProvider.class);
        AivaResponseComposerProperties properties = new AivaResponseComposerProperties();
        properties.setEnabled(true);
        PatientPortalCareAiResponseComposerService service = new PatientPortalCareAiResponseComposerService(router, new ObjectMapper(), properties);
        when(router.resolveCandidates(AiTaskType.GENERIC_COPILOT)).thenReturn(List.of(gemini, groq));
        when(gemini.providerName()).thenReturn("GEMINI");
        when(groq.providerName()).thenReturn("GROQ");
        when(gemini.complete(any(AiProviderRequest.class))).thenThrow(AiProviderException.retryable(
                "Gemini quota exceeded",
                429,
                "GEMINI",
                "gemini-1.5-flash",
                null,
                null
        ));
        when(groq.complete(any(AiProviderRequest.class))).thenReturn(response("\"आपकी अपॉइंटमेंट सफलतापूर्वक बुक हो गई है।\""));

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
    void composerDisabledReturnsRawText() {
        AiProviderRouter router = org.mockito.Mockito.mock(AiProviderRouter.class);
        AivaResponseComposerProperties properties = new AivaResponseComposerProperties();
        properties.setEnabled(false);
        PatientPortalCareAiResponseComposerService service = new PatientPortalCareAiResponseComposerService(router, new ObjectMapper(), properties);

        String raw = "Appointment booked successfully.";
        String polished = service.compose(raw, "booked_success", "en", "BOOK_APPOINTMENT", service.safeStructuredFacts(null, null, null, null, List.of(), List.of(), false, null));

        assertThat(polished).isEqualTo(raw);
        verify(router, never()).resolveCandidates(any());
    }

    @Test
    void enabledComposerCallsAiProviderAndReturnsPolishedText() {
        AiProviderRouter router = org.mockito.Mockito.mock(AiProviderRouter.class);
        AiProvider provider = org.mockito.Mockito.mock(AiProvider.class);
        AivaResponseComposerProperties properties = new AivaResponseComposerProperties();
        properties.setEnabled(true);
        PatientPortalCareAiResponseComposerService service = new PatientPortalCareAiResponseComposerService(router, new ObjectMapper(), properties);
        when(router.resolveCandidates(AiTaskType.GENERIC_COPILOT)).thenReturn(List.of(provider));
        when(provider.providerName()).thenReturn("GEMINI");
        when(provider.complete(any(AiProviderRequest.class))).thenReturn(response("आपकी अपॉइंटमेंट सफलतापूर्वक बुक हो गई है।"));

        String polished = service.compose(
                "Appointment booked successfully.",
                "booked_success",
                "en",
                "BOOK_APPOINTMENT",
                service.safeStructuredFacts(null, null, null, null, List.of(), List.of(), false, "BOOK_APPOINTMENT")
        );

        assertThat(polished).isEqualTo("आपकी अपॉइंटमेंट सफलतापूर्वक बुक हो गई है।");
        verify(router).resolveCandidates(AiTaskType.GENERIC_COPILOT);
        verify(provider).complete(any(AiProviderRequest.class));
    }

    private AiProviderResponse response(String outputText) {
        return new AiProviderResponse(
                "provider",
                "model",
                outputText,
                null,
                BigDecimal.valueOf(0.9),
                null
        );
    }
}
