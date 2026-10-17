package com.deepthoughtnet.clinic.api.platform.communicationtest;

import com.deepthoughtnet.clinic.api.carepilot.CarePilotMessagingStatusService;
import com.deepthoughtnet.clinic.carepilot.messaging.service.MessageOrchestratorService;
import com.deepthoughtnet.clinic.carepilot.ai_call.provider.DotVoiceMediaStreamClient;
import com.deepthoughtnet.clinic.carepilot.ai_call.service.VoiceConversationContextRegistry;
import com.deepthoughtnet.clinic.carepilot.ai_call.service.VoiceResponseInterpreter;
import com.deepthoughtnet.clinic.tts.spi.SpeechSynthesisResult;
import com.deepthoughtnet.clinic.messaging.email.Msg91EmailMessageProvider;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import com.deepthoughtnet.clinic.messaging.spi.MessageResult;
import com.deepthoughtnet.clinic.messaging.spi.MessageProvider;
import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.deepthoughtnet.clinic.voice.spi.VoiceCallProvider;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static com.deepthoughtnet.clinic.api.platform.communicationtest.CommunicationTestDtos.TestRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunicationTestServiceTest {
    private final CommunicationTestService service = new CommunicationTestService(
            mock(CarePilotMessagingStatusService.class),
            mock(MessageOrchestratorService.class),
            List.of(),
            List.of()
    );

    @Test
    void voiceWithoutAdapterIsNotReportedAsSuccess() {
        var result = service.voice(UUID.randomUUID(), new TestRequest(
                "+919876543210", null, "Provider test", "en-IN", null, null), "corr-1");

        assertFalse(result.success());
        assertEquals("NOT_CONFIGURED", result.failureCategory());
        assertEquals("VOICE_NOT_CONFIGURED", result.provider());
    }

    @Test
    void invalidEmailIsRejectedBeforeDispatch() {
        assertThrows(IllegalArgumentException.class, () -> service.email(UUID.randomUUID(), new TestRequest(
                "not-an-email", "Subject", "Body", "en-IN", null, null), "corr-1"));
    }

    @Test
    void mockVoiceProviderIsNotOperationalAndIsNeverCalled() {
        VoiceCallProvider mockProvider = mock(VoiceCallProvider.class);
        when(mockProvider.providerName()).thenReturn("mock-voice");
        when(mockProvider.isReady()).thenReturn(true);
        CommunicationTestService isolated = new CommunicationTestService(
                mock(CarePilotMessagingStatusService.class), mock(MessageOrchestratorService.class), List.of(), List.of(mockProvider));

        var health = isolated.health("corr-1");
        var voice = health.channels().stream().filter(row -> "VOICE".equals(row.channel())).findFirst().orElseThrow();
        assertEquals("NOT_CONFIGURED", voice.status());
        assertFalse(voice.configured());
        var result = isolated.voice(UUID.randomUUID(), new TestRequest(
                "+919876543210", null, "Provider test", "en-IN", null, null), "corr-1");
        assertFalse(result.success());
        assertEquals("NOT_CONFIGURED", result.failureCategory());
        verify(mockProvider, never()).placeCall(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void realReadyVoiceProviderRemainsOperational() {
        VoiceCallProvider provider = mock(VoiceCallProvider.class);
        when(provider.providerName()).thenReturn("dotvoice");
        when(provider.isReady()).thenReturn(true);
        CommunicationTestService isolated = new CommunicationTestService(
                mock(CarePilotMessagingStatusService.class), mock(MessageOrchestratorService.class), List.of(), List.of(provider));

        var voice = isolated.health("corr-1").channels().stream().filter(row -> "VOICE".equals(row.channel())).findFirst().orElseThrow();
        assertEquals("READY", voice.status());
        assertEquals("dotvoice", voice.provider());
    }

    @Test
    void requestedRealVoiceProviderIsSelectedForPlatformTest() {
        VoiceCallProvider provider = mock(VoiceCallProvider.class);
        when(provider.providerName()).thenReturn("dotvoice");
        when(provider.isReady()).thenReturn(true);
        when(provider.placeCall(org.mockito.ArgumentMatchers.any())).thenReturn(
                new com.deepthoughtnet.clinic.voice.spi.VoiceCallResult(
                        com.deepthoughtnet.clinic.voice.spi.VoiceCallStatus.QUEUED,
                        "dotvoice", "call-1", null, java.time.OffsetDateTime.now(), null, null));
        CommunicationTestService isolated = new CommunicationTestService(
                mock(CarePilotMessagingStatusService.class), mock(MessageOrchestratorService.class), List.of(), List.of(provider));

        var result = isolated.voice(null, new TestRequest(
                "+919876543210", null, "Connectivity test", "en-IN", null, null, "dotvoice"), "corr-1");

        assertEquals("dotvoice", result.provider());
        assertEquals("call-1", result.providerRequestId());
        verify(provider).placeCall(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void interactiveResponseModeRegistersCallContextAndStartsNonBlockingPlayback() {
        VoiceCallProvider provider = mock(VoiceCallProvider.class);
        when(provider.providerName()).thenReturn("dotvoice");
        when(provider.isReady()).thenReturn(true);
        when(provider.placeCall(org.mockito.ArgumentMatchers.any())).thenReturn(
                new com.deepthoughtnet.clinic.voice.spi.VoiceCallResult(
                        com.deepthoughtnet.clinic.voice.spi.VoiceCallStatus.QUEUED,
                        "dotvoice", "interactive-call-1", null, java.time.OffsetDateTime.now(), null, null));
        ReminderVoiceRenderer renderer = mock(ReminderVoiceRenderer.class);
        when(renderer.render(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new ReminderVoiceRenderer.RenderedReminder(
                        new SpeechSynthesisResult(testWav(), "audio/wav", 1), null, "piper"));
        DotVoiceMediaStreamClient stream = mock(DotVoiceMediaStreamClient.class);
        when(stream.prepare()).thenReturn(true);
        when(stream.startPlayback(org.mockito.ArgumentMatchers.eq("interactive-call-1"), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(true);
        VoiceConversationContextRegistry registry = new VoiceConversationContextRegistry();
        CommunicationTestService isolated = new CommunicationTestService(
                mock(CarePilotMessagingStatusService.class), mock(MessageOrchestratorService.class), List.of(), List.of(provider),
                null, renderer, stream, mock(com.deepthoughtnet.clinic.api.voice.VoiceOrchestratorService.class),
                registry, new VoiceResponseInterpreter());

        var result = isolated.voice(null, new TestRequest(
                "+919876543210", null, "This is a test call.", "hi-IN",
                CommunicationTestDtos.TestMode.INTERACTIVE_RESPONSE,
                CommunicationTestDtos.Scenario.YES_NO_CONFIRMATION, "dotvoice"), "corr-1");

        assertEquals("INITIATED", result.status());
        assertEquals("interactive-call-1", result.providerRequestId());
        assertEquals(result.requestId(), registry.find("interactive-call-1").orElseThrow().executionId().toString());
        verify(stream).startPlayback(org.mockito.ArgumentMatchers.eq("interactive-call-1"), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void interactiveInboundSttUsesCorrelatedTenantAndLanguageWithoutHttpRequestContext() {
        VoiceCallProvider provider = mock(VoiceCallProvider.class);
        when(provider.providerName()).thenReturn("dotvoice");
        when(provider.isReady()).thenReturn(true);
        when(provider.placeCall(org.mockito.ArgumentMatchers.any())).thenReturn(
                new com.deepthoughtnet.clinic.voice.spi.VoiceCallResult(
                        com.deepthoughtnet.clinic.voice.spi.VoiceCallStatus.QUEUED,
                        "dotvoice", "stt-call-1", null, java.time.OffsetDateTime.now(), null, null));
        ReminderVoiceRenderer renderer = mock(ReminderVoiceRenderer.class);
        when(renderer.render(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new ReminderVoiceRenderer.RenderedReminder(
                        new SpeechSynthesisResult(testWav(), "audio/wav", 1), null, "piper"));
        DotVoiceMediaStreamClient stream = mock(DotVoiceMediaStreamClient.class);
        when(stream.prepare()).thenReturn(true);
        when(stream.startPlayback(org.mockito.ArgumentMatchers.eq("stt-call-1"), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(true);
        com.deepthoughtnet.clinic.api.voice.VoiceOrchestratorService orchestrator = mock(com.deepthoughtnet.clinic.api.voice.VoiceOrchestratorService.class);
        when(orchestrator.transcribeBufferedAudio(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(byte[].class),
                org.mockito.ArgumentMatchers.eq("audio/wav"), org.mockito.ArgumentMatchers.eq("dotvoice-inbound.wav"),
                org.mockito.ArgumentMatchers.eq("hi-IN")))
                .thenReturn(new com.deepthoughtnet.clinic.api.voice.spi.VoiceTranscriptionResult("haan", "sarvam", null));
        VoiceConversationContextRegistry registry = new VoiceConversationContextRegistry();
        CommunicationTestService isolated = new CommunicationTestService(
                mock(CarePilotMessagingStatusService.class), mock(MessageOrchestratorService.class), List.of(), List.of(provider),
                null, renderer, stream, orchestrator, registry, new VoiceResponseInterpreter());

        var initial = isolated.voice(null, new TestRequest(
                "+919876543210", null, "This is a test call.", "hi-IN",
                CommunicationTestDtos.TestMode.INTERACTIVE_RESPONSE,
                CommunicationTestDtos.Scenario.YES_NO_CONFIRMATION, "dotvoice"), "corr-1");
        isolated.completeVoiceResult("stt-call-1", testWav(), "normal");

        var result = isolated.voiceResult(initial.testExecutionId());
        assertEquals("RESPONSE_CAPTURED", result.status());
        assertEquals("haan", result.rawTranscript());
        assertEquals("YES", result.normalizedIntent());
        verify(orchestrator).transcribeBufferedAudio(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(byte[].class),
                org.mockito.ArgumentMatchers.eq("audio/wav"), org.mockito.ArgumentMatchers.eq("dotvoice-inbound.wav"),
                org.mockito.ArgumentMatchers.eq("hi-IN"));
    }

    @Test
    void interactiveSttFailureStoresSafeDiagnostic() {
        VoiceCallProvider provider = mock(VoiceCallProvider.class);
        when(provider.providerName()).thenReturn("dotvoice");
        when(provider.isReady()).thenReturn(true);
        when(provider.placeCall(org.mockito.ArgumentMatchers.any())).thenReturn(
                new com.deepthoughtnet.clinic.voice.spi.VoiceCallResult(
                        com.deepthoughtnet.clinic.voice.spi.VoiceCallStatus.QUEUED,
                        "dotvoice", "stt-call-failure", null, java.time.OffsetDateTime.now(), null, null));
        ReminderVoiceRenderer renderer = mock(ReminderVoiceRenderer.class);
        when(renderer.render(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new ReminderVoiceRenderer.RenderedReminder(
                        new SpeechSynthesisResult(testWav(), "audio/wav", 1), null, "piper"));
        DotVoiceMediaStreamClient stream = mock(DotVoiceMediaStreamClient.class);
        when(stream.prepare()).thenReturn(true);
        when(stream.startPlayback(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList())).thenReturn(true);
        var orchestrator = mock(com.deepthoughtnet.clinic.api.voice.VoiceOrchestratorService.class);
        when(orchestrator.transcribeBufferedAudio(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(byte[].class),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new IllegalStateException("Missing RequestContext (tenant header / interceptor not applied?)"));
        var isolated = new CommunicationTestService(mock(CarePilotMessagingStatusService.class), mock(MessageOrchestratorService.class),
                List.of(), List.of(provider), null, renderer, stream, orchestrator,
                new VoiceConversationContextRegistry(), new VoiceResponseInterpreter());

        var initial = isolated.voice(null, new TestRequest("+919876543210", null, "Test", "hi-IN",
                CommunicationTestDtos.TestMode.INTERACTIVE_RESPONSE, CommunicationTestDtos.Scenario.YES_NO_CONFIRMATION, "dotvoice"), "corr-1");
        isolated.completeVoiceResult("stt-call-failure", testWav(), "normal");

        var result = isolated.voiceResult(initial.testExecutionId());
        assertEquals("FAILED", result.status());
        assertEquals("STT failed: Missing RequestContext (tenant header / interceptor not applied?)", result.failureMessage());
    }

    @Test
    void liveYesProcessesBeforeStreamStopThenClosesAndRequestsHangup() {
        VoiceCallProvider provider = interactiveProvider("live-yes");
        ReminderVoiceRenderer renderer = testRenderer();
        DotVoiceMediaStreamClient stream = mock(DotVoiceMediaStreamClient.class);
        when(stream.prepare()).thenReturn(true);
        when(stream.startPlayback(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList())).thenReturn(true);
        var orchestrator = mock(com.deepthoughtnet.clinic.api.voice.VoiceOrchestratorService.class);
        when(orchestrator.transcribeBufferedAudio(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(byte[].class),
                org.mockito.ArgumentMatchers.eq("audio/wav"), org.mockito.ArgumentMatchers.eq("dotvoice-inbound.wav"), org.mockito.ArgumentMatchers.eq("hi-IN")))
                .thenReturn(new com.deepthoughtnet.clinic.api.voice.spi.VoiceTranscriptionResult("haan", "sarvam", null));
        var isolated = interactiveService(provider, renderer, stream, orchestrator);

        var initial = isolated.voice(null, new TestRequest("+919876543210", null, "Test", "hi-IN",
                CommunicationTestDtos.TestMode.INTERACTIVE_RESPONSE, CommunicationTestDtos.Scenario.YES_NO_CONFIRMATION, "dotvoice"), "corr-1");
        isolated.processLiveUtterance("live-yes", testWav());

        var captured = isolated.voiceResult(initial.testExecutionId());
        assertEquals("CLOSING", captured.status());
        assertEquals("YES", captured.normalizedIntent());
        verify(stream, org.mockito.Mockito.times(2)).startPlayback(org.mockito.ArgumentMatchers.eq("live-yes"), org.mockito.ArgumentMatchers.anyList());
        isolated.onClosingPlaybackComplete("live-yes");
        verify(stream).hangup("live-yes");
    }

    @Test
    void unclearIsRepromptedOnceThenSecondUnclearCloses() {
        VoiceCallProvider provider = interactiveProvider("live-unclear");
        ReminderVoiceRenderer renderer = testRenderer();
        DotVoiceMediaStreamClient stream = mock(DotVoiceMediaStreamClient.class);
        when(stream.prepare()).thenReturn(true);
        when(stream.startPlayback(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList())).thenReturn(true);
        var orchestrator = mock(com.deepthoughtnet.clinic.api.voice.VoiceOrchestratorService.class);
        when(orchestrator.transcribeBufferedAudio(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(byte[].class),
                org.mockito.ArgumentMatchers.eq("audio/wav"), org.mockito.ArgumentMatchers.eq("dotvoice-inbound.wav"), org.mockito.ArgumentMatchers.eq("hi-IN")))
                .thenReturn(new com.deepthoughtnet.clinic.api.voice.spi.VoiceTranscriptionResult("maybe", "sarvam", null))
                .thenReturn(new com.deepthoughtnet.clinic.api.voice.spi.VoiceTranscriptionResult("perhaps", "sarvam", null));
        var isolated = interactiveService(provider, renderer, stream, orchestrator);

        var initial = isolated.voice(null, new TestRequest("+919876543210", null, "Test", "hi-IN",
                CommunicationTestDtos.TestMode.INTERACTIVE_RESPONSE, CommunicationTestDtos.Scenario.YES_NO_CONFIRMATION, "dotvoice"), "corr-1");
        isolated.processLiveUtterance("live-unclear", testWav());
        assertEquals("REPROMPTING", isolated.voiceResult(initial.testExecutionId()).status());
        isolated.processLiveUtterance("live-unclear", testWav());
        assertEquals("CLOSING", isolated.voiceResult(initial.testExecutionId()).status());
        assertEquals("UNCLEAR", isolated.voiceResult(initial.testExecutionId()).normalizedIntent());
        verify(stream, org.mockito.Mockito.times(3)).startPlayback(org.mockito.ArgumentMatchers.eq("live-unclear"), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void noResponseUsesFollowUpClosingAndHangup() {
        VoiceCallProvider provider = interactiveProvider("live-no-response");
        ReminderVoiceRenderer renderer = testRenderer();
        DotVoiceMediaStreamClient stream = mock(DotVoiceMediaStreamClient.class);
        when(stream.prepare()).thenReturn(true);
        when(stream.startPlayback(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList())).thenReturn(true);
        var isolated = interactiveService(provider, renderer, stream, mock(com.deepthoughtnet.clinic.api.voice.VoiceOrchestratorService.class));

        var initial = isolated.voice(null, new TestRequest("+919876543210", null, "Test", "hi-IN",
                CommunicationTestDtos.TestMode.INTERACTIVE_RESPONSE, CommunicationTestDtos.Scenario.YES_NO_CONFIRMATION, "dotvoice"), "corr-1");
        isolated.processNoResponse("live-no-response");

        assertEquals("CLOSING", isolated.voiceResult(initial.testExecutionId()).status());
        assertEquals("NO_RESPONSE", isolated.voiceResult(initial.testExecutionId()).normalizedIntent());
        isolated.onClosingPlaybackComplete("live-no-response");
        verify(stream).hangup("live-no-response");
    }

    private VoiceCallProvider interactiveProvider(String callId) {
        VoiceCallProvider provider = mock(VoiceCallProvider.class);
        when(provider.providerName()).thenReturn("dotvoice");
        when(provider.isReady()).thenReturn(true);
        when(provider.placeCall(org.mockito.ArgumentMatchers.any())).thenReturn(new com.deepthoughtnet.clinic.voice.spi.VoiceCallResult(
                com.deepthoughtnet.clinic.voice.spi.VoiceCallStatus.QUEUED, "dotvoice", callId, null,
                java.time.OffsetDateTime.now(), null, null));
        return provider;
    }

    private ReminderVoiceRenderer testRenderer() {
        ReminderVoiceRenderer renderer = mock(ReminderVoiceRenderer.class);
        when(renderer.render(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new ReminderVoiceRenderer.RenderedReminder(new SpeechSynthesisResult(testWav(), "audio/wav", 1), null, "piper"));
        return renderer;
    }

    private CommunicationTestService interactiveService(VoiceCallProvider provider, ReminderVoiceRenderer renderer,
                                                         DotVoiceMediaStreamClient stream,
                                                         com.deepthoughtnet.clinic.api.voice.VoiceOrchestratorService orchestrator) {
        return new CommunicationTestService(mock(CarePilotMessagingStatusService.class), mock(MessageOrchestratorService.class),
                List.of(), List.of(provider), null, renderer, stream, orchestrator,
                new VoiceConversationContextRegistry(), new VoiceResponseInterpreter());
    }

    @Test
    void unconfiguredMsg91IsShownButCannotSend() {
        Msg91EmailMessageProvider msg91 = mock(Msg91EmailMessageProvider.class);
        when(msg91.providerName()).thenReturn("msg91-email-smtp");
        when(msg91.isConfigured()).thenReturn(false);
        CommunicationTestService isolated = new CommunicationTestService(
                mock(CarePilotMessagingStatusService.class), mock(MessageOrchestratorService.class), List.of(), List.of(), msg91);

        var health = isolated.health("corr-1").channels().stream()
                .filter(row -> "msg91-email-smtp".equals(row.provider())).findFirst().orElseThrow();
        assertEquals("NOT_CONFIGURED", health.status());
        assertFalse(health.supportsTest());

        var result = isolated.email(UUID.randomUUID(), new TestRequest(
                "p@example.com", "Subject", "Body", "en-IN", null, null, "msg91-email-smtp"), "corr-1");
        assertFalse(result.success());
        assertEquals("NOT_CONFIGURED", result.failureCategory());
        verify(msg91, never()).send(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void configuredMsg91IsExplicitlySelectedWithoutChangingOrchestrator() {
        Msg91EmailMessageProvider msg91 = mock(Msg91EmailMessageProvider.class);
        when(msg91.providerName()).thenReturn("msg91-email-smtp");
        when(msg91.isConfigured()).thenReturn(true);
        when(msg91.send(org.mockito.ArgumentMatchers.any())).thenReturn(new MessageResult(
                true, MessageDeliveryStatus.SENT, "msg91-email-smtp", "msg-1", null, null, java.time.OffsetDateTime.now()));
        MessageOrchestratorService orchestrator = mock(MessageOrchestratorService.class);
        CommunicationTestService isolated = new CommunicationTestService(
                mock(CarePilotMessagingStatusService.class), orchestrator, List.of(), List.of(), msg91);

        var result = isolated.email(null, new TestRequest(
                "p@example.com", "Subject", "Body", "en-IN", null, null, "msg91-email-smtp"), "corr-1");
        assertEquals("msg91-email-smtp", result.provider());
        assertEquals("msg-1", result.providerRequestId());
        verify(msg91).send(org.mockito.ArgumentMatchers.any());
        verify(orchestrator, never()).send(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void platformScopedCarePilotEmailDoesNotRequireTenant() {
        MessageProvider carePilot = mock(MessageProvider.class);
        when(carePilot.providerName()).thenReturn("carepilot-email-smtp");
        when(carePilot.supports(MessageChannel.EMAIL)).thenReturn(true);
        MessageOrchestratorService orchestrator = mock(MessageOrchestratorService.class);
        when(orchestrator.send(org.mockito.ArgumentMatchers.any())).thenReturn(new MessageResult(
                true, MessageDeliveryStatus.SENT, "carepilot-email-smtp", "msg-1", null, null, java.time.OffsetDateTime.now()));
        CommunicationTestService isolated = new CommunicationTestService(
                mock(CarePilotMessagingStatusService.class), orchestrator, List.of(carePilot), List.of());

        var result = isolated.email(null, new TestRequest(
                "p@example.com", "Subject", "Body", "en-IN", null, null, "carepilot-email-smtp"), "corr-1");

        assertEquals("carepilot-email-smtp", result.provider());
        verify(orchestrator).send(org.mockito.ArgumentMatchers.argThat(request -> request.tenantId() == null));
    }

    @Test
    void reminderPlaybackSynthesizesBeforeOriginationAndUsesReturnedCallId() {
        VoiceCallProvider provider = mock(VoiceCallProvider.class);
        when(provider.providerName()).thenReturn("dotvoice");
        when(provider.isReady()).thenReturn(true);
        when(provider.placeCall(org.mockito.ArgumentMatchers.any())).thenReturn(
                new com.deepthoughtnet.clinic.voice.spi.VoiceCallResult(
                        com.deepthoughtnet.clinic.voice.spi.VoiceCallStatus.QUEUED,
                        "dotvoice", "call-1", null, java.time.OffsetDateTime.now(), null, null));
        ReminderVoiceRenderer renderer = mock(ReminderVoiceRenderer.class);
        when(renderer.render(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new ReminderVoiceRenderer.RenderedReminder(
                        new SpeechSynthesisResult(testWav(), "audio/wav", 1), null, "piper"));
        DotVoiceMediaStreamClient stream = mock(DotVoiceMediaStreamClient.class);
        when(stream.prepare()).thenReturn(true);
        when(stream.play(org.mockito.ArgumentMatchers.eq("call-1"), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(new DotVoiceMediaStreamClient.PlaybackResult(true, "PLAYBACK_COMPLETED", null, 1, "completed"));
        CommunicationTestService isolated = new CommunicationTestService(
                mock(CarePilotMessagingStatusService.class), mock(MessageOrchestratorService.class), List.of(), List.of(provider), null, renderer, stream);

        var result = isolated.voice(null, new TestRequest(
                "+919876543210", null, "This is a test reminder.", "en-IN",
                CommunicationTestDtos.TestMode.REMINDER_SIMULATION,
                CommunicationTestDtos.Scenario.APPOINTMENT_REMINDER, "dotvoice"), "corr-1");

        assertEquals("PLAYBACK_COMPLETED", result.status());
        verify(stream).play(org.mockito.ArgumentMatchers.eq("call-1"), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void failedReminderTtsDoesNotOriginateBillableCall() {
        VoiceCallProvider provider = mock(VoiceCallProvider.class);
        when(provider.providerName()).thenReturn("dotvoice");
        when(provider.isReady()).thenReturn(true);
        ReminderVoiceRenderer renderer = mock(ReminderVoiceRenderer.class);
        when(renderer.render(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new ReminderVoiceRenderer.RenderedReminder(null, "TTS_SYNTHESIS_FAILED", "elevenlabs"));
        CommunicationTestService isolated = new CommunicationTestService(
                mock(CarePilotMessagingStatusService.class), mock(MessageOrchestratorService.class), List.of(), List.of(provider), null, renderer, mock(DotVoiceMediaStreamClient.class));

        var result = isolated.voice(null, new TestRequest(
                "+919876543210", null, "This is a test reminder.", "en-IN",
                CommunicationTestDtos.TestMode.REMINDER_SIMULATION,
                CommunicationTestDtos.Scenario.APPOINTMENT_REMINDER, "dotvoice"), "corr-1");

        assertEquals("TTS_SYNTHESIS_FAILED", result.failureCategory());
        verify(provider, never()).placeCall(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unsupportedTtsFormatDoesNotOriginateBillableCall() {
        VoiceCallProvider provider = mock(VoiceCallProvider.class);
        when(provider.providerName()).thenReturn("dotvoice");
        when(provider.isReady()).thenReturn(true);
        ReminderVoiceRenderer renderer = mock(ReminderVoiceRenderer.class);
        when(renderer.render(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new ReminderVoiceRenderer.RenderedReminder(
                        new SpeechSynthesisResult(new byte[]{1, 2, 3}, "audio/mpeg", 1), null, "elevenlabs"));
        CommunicationTestService isolated = new CommunicationTestService(
                mock(CarePilotMessagingStatusService.class), mock(MessageOrchestratorService.class), List.of(), List.of(provider), null, renderer, mock(DotVoiceMediaStreamClient.class));

        var result = isolated.voice(null, new TestRequest(
                "+919876543210", null, "This is a test reminder.", "en-IN",
                CommunicationTestDtos.TestMode.REMINDER_SIMULATION,
                CommunicationTestDtos.Scenario.APPOINTMENT_REMINDER, "dotvoice"), "corr-1");

        assertEquals("AUDIO_FORMAT_FAILED", result.failureCategory());
        verify(provider, never()).placeCall(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void streamMustReceiveReadyBeforeReminderOrigination() {
        VoiceCallProvider provider = mock(VoiceCallProvider.class);
        when(provider.providerName()).thenReturn("dotvoice");
        when(provider.isReady()).thenReturn(true);
        ReminderVoiceRenderer renderer = mock(ReminderVoiceRenderer.class);
        when(renderer.render(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new ReminderVoiceRenderer.RenderedReminder(new SpeechSynthesisResult(testWav(), "audio/wav", 1), null, "piper"));
        DotVoiceMediaStreamClient stream = mock(DotVoiceMediaStreamClient.class);
        when(stream.prepare()).thenReturn(false);
        CommunicationTestService isolated = new CommunicationTestService(
                mock(CarePilotMessagingStatusService.class), mock(MessageOrchestratorService.class), List.of(), List.of(provider), null, renderer, stream);

        var result = isolated.voice(null, new TestRequest(
                "+919876543210", null, "This is a test reminder.", "en-IN",
                CommunicationTestDtos.TestMode.REMINDER_SIMULATION,
                CommunicationTestDtos.Scenario.APPOINTMENT_REMINDER, "dotvoice"), "corr-1");

        assertEquals("STREAM_READY_NOT_RECEIVED", result.failureCategory());
        verify(provider, never()).placeCall(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void inboundCallKeepsExactCallerAndDidAndCapturesOnlyFirstTranscript() {
        ReminderVoiceRenderer renderer = testRenderer();
        DotVoiceMediaStreamClient stream = mock(DotVoiceMediaStreamClient.class);
        when(stream.startPlayback(org.mockito.ArgumentMatchers.eq("inbound-1"), org.mockito.ArgumentMatchers.anyList())).thenReturn(true);
        var orchestrator = mock(com.deepthoughtnet.clinic.api.voice.VoiceOrchestratorService.class);
        when(orchestrator.transcribeBufferedAudio(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(byte[].class),
                org.mockito.ArgumentMatchers.eq("audio/wav"), org.mockito.ArgumentMatchers.eq("dotvoice-inbound.wav"),
                org.mockito.ArgumentMatchers.eq("hi-IN")))
                .thenReturn(new com.deepthoughtnet.clinic.api.voice.spi.VoiceTranscriptionResult("Mujhe appointment book karna hai", "sarvam", null));
        CommunicationTestService isolated = new CommunicationTestService(
                mock(CarePilotMessagingStatusService.class), mock(MessageOrchestratorService.class), List.of(), List.of(),
                null, renderer, stream, orchestrator, new VoiceConversationContextRegistry(), new VoiceResponseInterpreter());

        isolated.registerInboundCall("inbound-1", "0917XXXXXXXX", "00917969812868");
        assertEquals("RINGING", isolated.inboundVoiceResult("inbound-1").status());
        isolated.markInboundAnswered("inbound-1");
        isolated.processInboundFirstUtterance("inbound-1", testWav());
        isolated.processInboundFirstUtterance("inbound-1", testWav());

        var result = isolated.inboundVoiceResult("inbound-1");
        assertEquals("0917XXXXXXXX", result.callerPhoneNumber());
        assertEquals("00917969812868", result.didNumber());
        assertEquals("Mujhe appointment book karna hai", result.firstTranscript());
        verify(orchestrator).transcribeBufferedAudio(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(byte[].class),
                org.mockito.ArgumentMatchers.eq("audio/wav"), org.mockito.ArgumentMatchers.eq("dotvoice-inbound.wav"),
                org.mockito.ArgumentMatchers.eq("hi-IN"));
    }

    @Test
    void simultaneousInboundCallsKeepCallerAndTranscriptContextsSeparate() {
        ReminderVoiceRenderer renderer = testRenderer();
        DotVoiceMediaStreamClient stream = mock(DotVoiceMediaStreamClient.class);
        when(stream.startPlayback(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList())).thenReturn(true);
        var orchestrator = mock(com.deepthoughtnet.clinic.api.voice.VoiceOrchestratorService.class);
        when(orchestrator.transcribeBufferedAudio(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(byte[].class),
                org.mockito.ArgumentMatchers.eq("audio/wav"), org.mockito.ArgumentMatchers.eq("dotvoice-inbound.wav"),
                org.mockito.ArgumentMatchers.eq("hi-IN")))
                .thenAnswer(invocation -> invocation.getArgument(1) != null
                        ? new com.deepthoughtnet.clinic.api.voice.spi.VoiceTranscriptionResult(
                        ((byte[]) invocation.getArgument(1)).length > 300 ? "caller one" : "caller two", "sarvam", null) : null);
        CommunicationTestService isolated = new CommunicationTestService(
                mock(CarePilotMessagingStatusService.class), mock(MessageOrchestratorService.class), List.of(), List.of(),
                null, renderer, stream, orchestrator, new VoiceConversationContextRegistry(), new VoiceResponseInterpreter());

        isolated.registerInboundCall("inbound-a", "caller-a", "did-a");
        isolated.registerInboundCall("inbound-b", "caller-b", "did-b");
        isolated.processInboundFirstUtterance("inbound-a", testWav());
        isolated.processInboundFirstUtterance("inbound-b", testWav(160));

        assertEquals("caller-a", isolated.inboundVoiceResult("inbound-a").callerPhoneNumber());
        assertEquals("did-a", isolated.inboundVoiceResult("inbound-a").didNumber());
        assertEquals("caller one", isolated.inboundVoiceResult("inbound-a").firstTranscript());
        assertEquals("caller-b", isolated.inboundVoiceResult("inbound-b").callerPhoneNumber());
        assertEquals("did-b", isolated.inboundVoiceResult("inbound-b").didNumber());
        assertEquals("caller two", isolated.inboundVoiceResult("inbound-b").firstTranscript());
    }

    private byte[] testWav() {
        return testWav(320);
    }

    private byte[] testWav(int pcmBytes) {
        byte[] wav = new byte[44 + pcmBytes];
        java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(wav).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(wav.length - 8)
                .put("WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1)
                .putShort((short) 1).putInt(8000).putInt(16000).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(pcmBytes);
        return wav;
    }
}
