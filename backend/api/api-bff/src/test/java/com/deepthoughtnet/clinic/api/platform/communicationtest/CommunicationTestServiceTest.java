package com.deepthoughtnet.clinic.api.platform.communicationtest;

import com.deepthoughtnet.clinic.api.carepilot.CarePilotMessagingStatusService;
import com.deepthoughtnet.clinic.carepilot.messaging.service.MessageOrchestratorService;
import com.deepthoughtnet.clinic.carepilot.ai_call.provider.DotVoiceMediaStreamClient;
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

    private byte[] testWav() {
        byte[] wav = new byte[44 + 320];
        java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(wav).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(wav.length - 8)
                .put("WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1)
                .putShort((short) 1).putInt(8000).putInt(16000).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(320);
        return wav;
    }
}
