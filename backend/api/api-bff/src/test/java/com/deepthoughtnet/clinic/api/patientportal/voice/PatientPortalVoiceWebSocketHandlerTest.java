package com.deepthoughtnet.clinic.api.patientportal.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiConversationPersistenceService;
import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiConversationSessionSnapshot;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiConversationEntity;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiWorkflowEntity;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiStateResponse;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiProgressEvent;
import com.deepthoughtnet.clinic.api.patientportal.aivav2.AivaV2VoiceTurnConnector;
import com.deepthoughtnet.clinic.api.patientportal.voice.PatientPortalVoiceAssistantService.PatientPortalVoiceTurnResponse;
import com.deepthoughtnet.clinic.api.patientportal.voice.PatientPortalVoiceAssistantService.PatientPortalVoiceProgressAudio;
import com.deepthoughtnet.clinic.api.voice.VoiceTestProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class PatientPortalVoiceWebSocketHandlerTest {
    private static final String TENANT_ID = UUID.randomUUID().toString();
    private static final String PATIENT_ID = UUID.randomUUID().toString();
    private static final String APP_USER_ID = UUID.randomUUID().toString();

    @Test
    void explicitV2CareSessionUsesStableUtteranceIdentityAndSequence() throws Exception {
        PatientPortalVoiceAssistantService assistantService = mock(PatientPortalVoiceAssistantService.class);
        CareAiConversationPersistenceService persistenceService = mock(CareAiConversationPersistenceService.class);
        VoiceTestProperties properties = new VoiceTestProperties();
        properties.setAivaV2Enabled(true);
        PatientPortalVoiceTurnResponse response = new PatientPortalVoiceTurnResponse(
                "v2-turn-1", "show my appointments", "Here are your appointments.", null,
                null, null, "sarvam", "AIVA_V2", null,
                10L, 0L, 0L, 10L, 4L, null);
        when(assistantService.processAudioTurnV2(
                any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong(), any()))
                .thenReturn(response);
        PatientPortalVoiceWebSocketHandler handler = new PatientPortalVoiceWebSocketHandler(
                new ObjectMapper(), assistantService, properties, persistenceService);
        SessionFixture fixture = new SessionFixture(TENANT_ID, PATIENT_ID, APP_USER_ID, Set.of("PATIENT"), "v2-care-session");
        String audioBase64 = Base64.getEncoder().encodeToString("voice".getBytes(StandardCharsets.UTF_8));

        handler.afterConnectionEstablished(fixture.session);
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"session.start\",\"engine\":\"v2\",\"language\":\"en\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.chunk\",\"voiceUtteranceId\":\"u1\",\"sequence\":1,\"totalChunks\":1,\"audioBase64Chunk\":\"" + audioBase64 + "\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.end\",\"voiceUtteranceId\":\"u1\",\"totalChunks\":1}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.end\",\"voiceUtteranceId\":\"u1\",\"totalChunks\":1}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.chunk\",\"voiceUtteranceId\":\"u2\",\"sequence\":1,\"totalChunks\":1,\"audioBase64Chunk\":\"" + audioBase64 + "\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.end\",\"voiceUtteranceId\":\"u2\",\"totalChunks\":1}"));

        verify(assistantService, times(1)).processAudioTurnV2(
                any(), anyString(), anyString(), anyString(), eq("voice-v2-v2-care-session"), eq("voice-u1"), eq(1L), any());
        verify(assistantService, times(1)).processAudioTurnV2(
                any(), anyString(), anyString(), anyString(), eq("voice-v2-v2-care-session"), eq("voice-u2"), eq(2L), any());
        verify(assistantService, never()).processAudioTurn(any(), anyString(), anyString(), anyString(), any());
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"engine\":\"v2\""));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"turn.duplicate_replay\"")
                && payload.contains("\"voiceUtteranceId\":\"u1\""));
    }

    @Test
    void tenConsecutiveV2UtterancesKeepMonotonicSequenceWithoutStaleTurn() throws Exception {
        PatientPortalVoiceAssistantService assistantService = mock(PatientPortalVoiceAssistantService.class);
        CareAiConversationPersistenceService persistenceService = mock(CareAiConversationPersistenceService.class);
        VoiceTestProperties properties = new VoiceTestProperties();
        properties.setAivaV2Enabled(true);
        PatientPortalVoiceTurnResponse response = new PatientPortalVoiceTurnResponse(
                "v2-turn", "show my appointments", "Here are your appointments.", null,
                null, null, "sarvam", "AIVA_V2", null,
                10L, 0L, 0L, 10L, 4L, null);
        when(assistantService.processAudioTurnV2(
                any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong(), any()))
                .thenReturn(response);
        PatientPortalVoiceWebSocketHandler handler = new PatientPortalVoiceWebSocketHandler(
                new ObjectMapper(), assistantService, properties, persistenceService);
        SessionFixture fixture = new SessionFixture(TENANT_ID, PATIENT_ID, APP_USER_ID, Set.of("PATIENT"), "v2-ten-turn-session");
        String audioBase64 = Base64.getEncoder().encodeToString("voice".getBytes(StandardCharsets.UTF_8));

        handler.afterConnectionEstablished(fixture.session);
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"session.start\",\"engine\":\"v2\",\"language\":\"auto\"}"));
        for (int turn = 1; turn <= 10; turn++) {
            String utteranceId = "u" + turn;
            handler.handleForTest(fixture.session, new TextMessage(
                    "{\"type\":\"audio.chunk\",\"voiceUtteranceId\":\"" + utteranceId
                            + "\",\"sequence\":1,\"totalChunks\":1,\"audioBase64Chunk\":\"" + audioBase64 + "\"}"));
            handler.handleForTest(fixture.session, new TextMessage(
                    "{\"type\":\"audio.end\",\"voiceUtteranceId\":\"" + utteranceId + "\",\"totalChunks\":1}"));
        }

        org.mockito.ArgumentCaptor<Long> sequenceCaptor = org.mockito.ArgumentCaptor.forClass(Long.class);
        verify(assistantService, times(10)).processAudioTurnV2(
                any(), anyString(), anyString(), anyString(), anyString(), anyString(), sequenceCaptor.capture(), any());
        assertThat(sequenceCaptor.getAllValues()).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
        assertThat(fixture.payloads()).noneMatch(payload -> payload.contains("message arrived too late"));
        assertThat(fixture.payloads()).noneMatch(payload -> payload.contains("SEQUENCE_GAP"));
    }

    @Test
    void resumedV2SocketHydratesSequenceFromAuthoritativeSessionState() throws Exception {
        PatientPortalVoiceAssistantService assistantService = mock(PatientPortalVoiceAssistantService.class);
        CareAiConversationPersistenceService persistenceService = mock(CareAiConversationPersistenceService.class);
        AivaV2VoiceTurnConnector connector = mock(AivaV2VoiceTurnConnector.class);
        VoiceTestProperties properties = new VoiceTestProperties();
        properties.setAivaV2Enabled(true);
        when(connector.lastAcceptedVoiceTurnSequence(
                eq(UUID.fromString(TENANT_ID)), eq(UUID.fromString(PATIENT_ID)), eq("voice-v2-resumed-v2-session")))
                .thenReturn(7L);
        when(assistantService.processAudioTurnV2(
                any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong(), any()))
                .thenReturn(new PatientPortalVoiceTurnResponse(
                        "v2-turn-8", "show my appointments", "Here are your appointments.", null,
                        null, null, "sarvam", "AIVA_V2", null,
                        10L, 0L, 0L, 10L, 4L, null));
        PatientPortalVoiceWebSocketHandler handler = new PatientPortalVoiceWebSocketHandler(
                new ObjectMapper(), assistantService, properties, persistenceService, connector);
        SessionFixture fixture = new SessionFixture(TENANT_ID, PATIENT_ID, APP_USER_ID, Set.of("PATIENT"), "resumed-v2-session");
        String audioBase64 = Base64.getEncoder().encodeToString("voice".getBytes(StandardCharsets.UTF_8));

        handler.afterConnectionEstablished(fixture.session);
        handler.handleForTest(fixture.session, new TextMessage(
                "{\"type\":\"session.start\",\"engine\":\"v2\",\"language\":\"auto\",\"resumeSessionId\":\"resumed-v2-session\"}"));
        handler.handleForTest(fixture.session, new TextMessage(
                "{\"type\":\"audio.chunk\",\"voiceUtteranceId\":\"u8\",\"sequence\":1,\"totalChunks\":1,\"audioBase64Chunk\":\"" + audioBase64 + "\"}"));
        handler.handleForTest(fixture.session, new TextMessage(
                "{\"type\":\"audio.end\",\"voiceUtteranceId\":\"u8\",\"totalChunks\":1}"));

        verify(assistantService).processAudioTurnV2(
                any(), anyString(), anyString(), anyString(), eq("voice-v2-resumed-v2-session"), eq("voice-u8"), eq(8L), any());
    }

    @Test
    void v2CareSessionIsRejectedWithoutLegacyFallbackWhenFeatureIsDisabled() throws Exception {
        PatientPortalVoiceAssistantService assistantService = mock(PatientPortalVoiceAssistantService.class);
        VoiceTestProperties properties = new VoiceTestProperties();
        properties.setAivaV2Enabled(false);
        PatientPortalVoiceWebSocketHandler handler = new PatientPortalVoiceWebSocketHandler(
                new ObjectMapper(), assistantService, properties,
                mock(CareAiConversationPersistenceService.class));
        SessionFixture fixture = new SessionFixture(TENANT_ID, PATIENT_ID, APP_USER_ID, Set.of("PATIENT"), "v2-disabled-session");

        handler.afterConnectionEstablished(fixture.session);
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"session.start\",\"engine\":\"v2\"}"));

        verify(assistantService, never()).processAudioTurnV2(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong());
        verify(assistantService, never()).processAudioTurn(any(), anyString(), anyString(), anyString(), any());
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("AIVA V2 voice is currently unavailable"));
    }

    @Test
    void omittedEngineUsesV2ByDefault() throws Exception {
        PatientPortalVoiceAssistantService assistantService = mock(PatientPortalVoiceAssistantService.class);
        CareAiConversationPersistenceService persistenceService = mock(CareAiConversationPersistenceService.class);
        VoiceTestProperties properties = new VoiceTestProperties();
        properties.setAivaV2Enabled(true);
        when(assistantService.processAudioTurnV2(
                any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong(), any()))
                .thenReturn(new PatientPortalVoiceTurnResponse(
                        "v2-default-turn", "show my appointments", "Here are your appointments.", null,
                        null, null, "stt", "AIVA_V2", null,
                        1L, 1L, 1L, 3L, 10L, null));
        PatientPortalVoiceWebSocketHandler handler = new PatientPortalVoiceWebSocketHandler(
                new ObjectMapper(), assistantService, properties, persistenceService);
        SessionFixture fixture = new SessionFixture(TENANT_ID, PATIENT_ID, APP_USER_ID, Set.of("PATIENT"), "v2-default-session");
        String audioBase64 = Base64.getEncoder().encodeToString("voice".getBytes(StandardCharsets.UTF_8));

        handler.afterConnectionEstablished(fixture.session);
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"session.start\",\"language\":\"auto\"}"));
        handler.handleForTest(fixture.session, new TextMessage(
                "{\"type\":\"audio.chunk\",\"sequence\":1,\"totalChunks\":1,\"audioBase64Chunk\":\"" + audioBase64 + "\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.end\",\"totalChunks\":1}"));

        verify(assistantService).processAudioTurnV2(
                any(), anyString(), anyString(), anyString(), anyString(), anyString(), eq(1L), any());
        verify(assistantService, never()).processAudioTurn(any(), anyString(), anyString(), anyString(), any());
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"session.started\"")
                && payload.contains("\"engine\":\"v2\""));
    }

    @Test
    void patientVoiceUsesCareAiEngineAndReturnsAssistantEvents() throws Exception {
        PatientPortalVoiceAssistantService assistantService = mock(PatientPortalVoiceAssistantService.class);
        CareAiConversationPersistenceService persistenceService = mock(CareAiConversationPersistenceService.class);
        PatientPortalCareAiStateResponse state = new PatientPortalCareAiStateResponse(
                "en",
                "BOOK_APPOINTMENT",
                "Dr Neha Mehta",
                "Dermatology",
                null,
                "2026-06-10",
                "morning",
                "10:30",
                true,
                false,
                false,
                null,
                null,
                null,
                null,
                false,
                null,
                List.of("Dr Neha Mehta"),
                List.of(),
                List.of("10:30")
        );
        when(assistantService.processAudioTurn(any(), any(), any(), any(), any())).thenReturn(
                new PatientPortalVoiceTurnResponse(
                        "req-voice-1",
                        "I want to book an appointment.",
                        "Please confirm the 10:30 slot.",
                        state,
                        "audio/wav",
                        Base64.getEncoder().encodeToString("voice".getBytes(StandardCharsets.UTF_8)),
                        "faster-whisper",
                        "PATIENT_PORTAL_CAREAI",
                        "piper",
                        120L,
                        35L,
                        90L,
                        260L,
                        5L,
                        null
                )
        );
        PatientPortalVoiceWebSocketHandler handler = new PatientPortalVoiceWebSocketHandler(
                new ObjectMapper(),
                assistantService,
                new VoiceTestProperties(),
                persistenceService
        );
        SessionFixture fixture = new SessionFixture(TENANT_ID, PATIENT_ID, APP_USER_ID, Set.of("PATIENT"), "patient-session-1");
        String audioBase64 = Base64.getEncoder().encodeToString("voice".getBytes(StandardCharsets.UTF_8));

        handler.afterConnectionEstablished(fixture.session);
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"session.start\",\"engine\":\"legacy\",\"language\":\"auto\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.chunk\",\"sequence\":1,\"totalChunks\":1,\"filename\":\"patient-careai.webm\",\"audioBase64Chunk\":\"" + audioBase64 + "\",\"contentType\":\"audio/webm\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.end\",\"filename\":\"patient-careai.webm\",\"contentType\":\"audio/webm\",\"totalChunks\":1}"));

        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"session.started\""));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"transcript.final\"") && payload.contains("book an appointment"));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"assistant.text\"") && payload.contains("confirm the 10:30 slot"));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"assistant.audio.chunk\""));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"assistant.audio.end\"") && payload.contains("\"contentType\":\"audio/wav\""));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"turn.tts.complete\""));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"turn.complete\"") && payload.contains("\"currentIntent\":\"BOOK_APPOINTMENT\""));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"session.started\"") && payload.contains("\"voiceConfig\""));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"turn.complete\"") && payload.contains("\"totalDurationMs\":260"));
        int audioEndIndex = indexOfPayload(fixture.payloads(), "\"type\":\"assistant.audio.end\"");
        int ttsCompleteIndex = indexOfPayload(fixture.payloads(), "\"type\":\"turn.tts.complete\"");
        int turnCompleteIndex = indexOfPayload(fixture.payloads(), "\"type\":\"turn.complete\"");
        assertThat(audioEndIndex).isGreaterThanOrEqualTo(0);
        assertThat(ttsCompleteIndex).isGreaterThan(audioEndIndex);
        assertThat(turnCompleteIndex).isGreaterThan(ttsCompleteIndex);
        verify(assistantService).processAudioTurn(any(), any(), any(), any(), any());
    }

    @Test
    void liveCareAiProgressIsDeliveredAsSafeIntermediateEvent() throws Exception {
        PatientPortalVoiceAssistantService assistantService = mock(PatientPortalVoiceAssistantService.class);
        CareAiConversationPersistenceService persistenceService = mock(CareAiConversationPersistenceService.class);
        when(assistantService.synthesizeProgressAudio(any(), anyString()))
                .thenReturn(new PatientPortalVoiceProgressAudio(
                        "audio/wav",
                        Base64.getEncoder().encodeToString("progress-audio".getBytes(StandardCharsets.UTF_8)),
                        "elevenlabs"
                ));
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<PatientPortalCareAiProgressEvent> progress = invocation.getArgument(4);
            progress.accept(new PatientPortalCareAiProgressEvent(
                    "turn-1",
                    "skill-1",
                    "doctor.find",
                    "checking-doctors",
                    "WAITING_FOR_TOOL",
                    "Let me check available doctors."
            ));
            return new PatientPortalVoiceTurnResponse(
                    "request-1", "Find a doctor", "I found a doctor.", null,
                    null, null, "stt", "PATIENT_PORTAL_CAREAI", "tts",
                    1L, 1L, 1L, 3L, 10L, null
            );
        }).when(assistantService).processAudioTurn(any(), any(), any(), any(), any());

        PatientPortalVoiceWebSocketHandler handler = new PatientPortalVoiceWebSocketHandler(
                new ObjectMapper(), assistantService, new VoiceTestProperties(), persistenceService);
        SessionFixture fixture = new SessionFixture(TENANT_ID, PATIENT_ID, APP_USER_ID, Set.of("PATIENT"), "patient-progress-1");
        String audioBase64 = Base64.getEncoder().encodeToString("voice".getBytes(StandardCharsets.UTF_8));

        handler.afterConnectionEstablished(fixture.session);
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"session.start\",\"engine\":\"legacy\",\"language\":\"auto\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.chunk\",\"sequence\":1,\"totalChunks\":1,\"audioBase64Chunk\":\"" + audioBase64 + "\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.end\",\"totalChunks\":1}"));

        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"turn.progress\"")
                && payload.contains("\"progressKey\":\"checking-doctors\"")
                && payload.contains("\"state\":\"WAITING_FOR_TOOL\"")
                && payload.contains("Let me check available doctors."));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"assistant.progress.audio.chunk\"")
                && payload.contains("\"skillExecutionId\":\"skill-1\""));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"assistant.progress.audio.end\"")
                && payload.contains("\"contentType\":\"audio/wav\""));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"turn.complete\""));
        assertThat(fixture.payloads()).noneMatch(payload -> payload.contains("sessionToken") || payload.contains("Authorization"));
    }

    @Test
    void missingPatientContextIsRejected() throws Exception {
        PatientPortalVoiceWebSocketHandler handler = new PatientPortalVoiceWebSocketHandler(
                new ObjectMapper(),
                mock(PatientPortalVoiceAssistantService.class),
                new VoiceTestProperties(),
                mock(CareAiConversationPersistenceService.class)
        );
        SessionFixture fixture = new SessionFixture(TENANT_ID, null, APP_USER_ID, Set.of("PATIENT"), "patient-session-2");

        handler.afterConnectionEstablished(fixture.session);

        verify(fixture.session).close(any(CloseStatus.class));
    }

    @Test
    void reconnectWithResumeSessionIdMarksSessionResumed() throws Exception {
        PatientPortalVoiceAssistantService assistantService = mock(PatientPortalVoiceAssistantService.class);
        CareAiConversationPersistenceService persistenceService = mock(CareAiConversationPersistenceService.class);
        UUID tenantId = UUID.fromString(TENANT_ID);
        UUID patientId = UUID.fromString(PATIENT_ID);
        CareAiConversationEntity conversation = CareAiConversationEntity.create(
                tenantId,
                "PATIENT_PORTAL_VOICE",
                patientId,
                null,
                "resume-session-1"
        );
        CareAiWorkflowEntity workflow = CareAiWorkflowEntity.create(
                tenantId,
                conversation.getId(),
                "BOOK_APPOINTMENT",
                "COLLECTING_INFO",
                """
                {"intent":"BOOK_APPOINTMENT","doctorId":"doctor-neha","doctorName":"Dr Neha Mehta","preferredDate":"%s","preferredTimeWindow":"evening","slotChoices":[{"appointmentDate":"%s","slotTime":"17:00"},{"appointmentDate":"%s","slotTime":"17:30"}],"slotOptions":["17:00","17:30"]}
                """.formatted(LocalDate.now().plusDays(1), LocalDate.now().plusDays(1), LocalDate.now().plusDays(1)),
                "choose-slot",
                0
        );
        when(persistenceService.safeResumeSession(eq(tenantId), any(), eq(patientId), eq("resume-session-1"), any(), any(), eq(8)))
                .thenReturn(new CareAiConversationSessionSnapshot(conversation, workflow, null, List.of()));

        PatientPortalVoiceWebSocketHandler handler = new PatientPortalVoiceWebSocketHandler(
                new ObjectMapper(),
                assistantService,
                new VoiceTestProperties(),
                persistenceService
        );
        SessionFixture fixture = new SessionFixture(TENANT_ID, PATIENT_ID, APP_USER_ID, Set.of("PATIENT"), "patient-session-3");

        handler.afterConnectionEstablished(fixture.session);
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"session.start\",\"language\":\"auto\",\"resumeSessionId\":\"resume-session-1\"}"));

        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"session.started\"") && payload.contains("\"resumed\":true"));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"sessionId\":\"resume-session-1\""));
    }

    @Test
    void connectionCloseMarksVoiceDisconnected() throws Exception {
        CareAiConversationPersistenceService persistenceService = mock(CareAiConversationPersistenceService.class);
        PatientPortalVoiceWebSocketHandler handler = new PatientPortalVoiceWebSocketHandler(
                new ObjectMapper(),
                mock(PatientPortalVoiceAssistantService.class),
                new VoiceTestProperties(),
                persistenceService
        );
        SessionFixture fixture = new SessionFixture(TENANT_ID, PATIENT_ID, APP_USER_ID, Set.of("PATIENT"), "patient-session-4");

        handler.afterConnectionEstablished(fixture.session);
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"session.start\",\"language\":\"auto\",\"resumeSessionId\":\"resume-session-4\"}"));
        handler.afterConnectionClosed(fixture.session, CloseStatus.NORMAL);

        verify(persistenceService).safeMarkVoiceDisconnected(
                eq(UUID.fromString(TENANT_ID)),
                eq(UUID.fromString(PATIENT_ID)),
                eq("resume-session-4"),
                anyString()
        );
    }

    @Test
    void assistantFailuresAreSanitizedBeforeReachingThePatientClient() throws Exception {
        PatientPortalVoiceAssistantService assistantService = mock(PatientPortalVoiceAssistantService.class);
        CareAiConversationPersistenceService persistenceService = mock(CareAiConversationPersistenceService.class);
        when(assistantService.processAudioTurn(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("ElevenLabs synthesis failed: http://internal.example/token=secret"));

        PatientPortalVoiceWebSocketHandler handler = new PatientPortalVoiceWebSocketHandler(
                new ObjectMapper(),
                assistantService,
                new VoiceTestProperties(),
                persistenceService
        );
        SessionFixture fixture = new SessionFixture(TENANT_ID, PATIENT_ID, APP_USER_ID, Set.of("PATIENT"), "patient-session-5");
        String audioBase64 = Base64.getEncoder().encodeToString("voice".getBytes(StandardCharsets.UTF_8));

        handler.afterConnectionEstablished(fixture.session);
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"session.start\",\"engine\":\"legacy\",\"language\":\"auto\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.chunk\",\"sequence\":1,\"totalChunks\":1,\"filename\":\"patient-careai.webm\",\"audioBase64Chunk\":\"" + audioBase64 + "\",\"contentType\":\"audio/webm\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.end\",\"filename\":\"patient-careai.webm\",\"contentType\":\"audio/webm\",\"totalChunks\":1}"));

        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"error\"") && payload.contains("Voice playback unavailable."));
        assertThat(fixture.payloads()).noneMatch(payload -> payload.contains("secret"));
        assertThat(fixture.payloads()).noneMatch(payload -> payload.contains("ElevenLabs synthesis failed"));
    }

    @Test
    void terminalResponseStartsCloseAfterFinalPlaybackAndClosesNormally() throws Exception {
        PatientPortalVoiceAssistantService assistantService = mock(PatientPortalVoiceAssistantService.class);
        CareAiConversationPersistenceService persistenceService = mock(CareAiConversationPersistenceService.class);
        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getLive().setTerminalCloseSeconds(0);
        String audio = Base64.getEncoder().encodeToString("wav".getBytes(StandardCharsets.UTF_8));
        when(assistantService.processAudioTurn(any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new PatientPortalVoiceTurnResponse(
                        "terminal-1", "bye", "Goodbye. Take care.", null, "audio/wav", audio,
                        "stt", "llm", "tts", 1L, 1L, 1L, 3L, 3L, null, true));

        PatientPortalVoiceWebSocketHandler handler = new PatientPortalVoiceWebSocketHandler(
                new ObjectMapper(), assistantService, properties, persistenceService);
        SessionFixture fixture = new SessionFixture(TENANT_ID, PATIENT_ID, APP_USER_ID, Set.of("PATIENT"), "terminal-session");
        String input = Base64.getEncoder().encodeToString("voice".getBytes(StandardCharsets.UTF_8));
        handler.afterConnectionEstablished(fixture.session);
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"session.start\",\"engine\":\"legacy\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.chunk\",\"sequence\":1,\"totalChunks\":1,\"audioBase64Chunk\":\"" + input + "\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.end\",\"totalChunks\":1}"));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"conversation.terminal\""));
        verify(fixture.session, never()).close(CloseStatus.NORMAL);

        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.playback.complete\",\"turnIndex\":1}"));
        Thread.sleep(100);

        verify(fixture.session, times(1)).close(CloseStatus.NORMAL);
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"session.closed\"")
                && payload.contains("terminal_idle"));
    }

    @Test
    void validNewSpeechCancelsPendingTerminalClose() throws Exception {
        PatientPortalVoiceAssistantService assistantService = mock(PatientPortalVoiceAssistantService.class);
        CareAiConversationPersistenceService persistenceService = mock(CareAiConversationPersistenceService.class);
        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getLive().setTerminalCloseSeconds(1);
        String audio = Base64.getEncoder().encodeToString("wav".getBytes(StandardCharsets.UTF_8));
        when(assistantService.processAudioTurn(any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new PatientPortalVoiceTurnResponse(
                        "terminal-2", "bye", "Goodbye. Take care.", null, null, null,
                        "stt", "llm", null, 1L, 1L, 0L, 2L, 3L, null, true));

        PatientPortalVoiceWebSocketHandler handler = new PatientPortalVoiceWebSocketHandler(
                new ObjectMapper(), assistantService, properties, persistenceService);
        SessionFixture fixture = new SessionFixture(TENANT_ID, PATIENT_ID, APP_USER_ID, Set.of("PATIENT"), "terminal-cancel-session");
        handler.afterConnectionEstablished(fixture.session);
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"session.start\",\"engine\":\"legacy\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.chunk\",\"sequence\":1,\"totalChunks\":1,\"audioBase64Chunk\":\"" + audio + "\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.end\",\"totalChunks\":1}"));
        Thread.sleep(100);
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.chunk\",\"sequence\":1,\"totalChunks\":1,\"audioBase64Chunk\":\"" + audio + "\"}"));
        Thread.sleep(1100);

        verify(fixture.session, never()).close(CloseStatus.NORMAL);
    }

    private static final class SessionFixture {
        private final List<String> payloads = new ArrayList<>();
        private final WebSocketSession session;

        private SessionFixture(String tenantId, String patientId, String appUserId, Set<String> roles, String sessionId) throws Exception {
            this.session = mock(WebSocketSession.class);
            Map<String, Object> attrs = new HashMap<>();
            if (tenantId != null) {
                attrs.put("tenantId", tenantId);
            }
            if (patientId != null) {
                attrs.put("patientId", patientId);
            }
            if (appUserId != null) {
                attrs.put("appUserId", appUserId);
            }
            attrs.put("roles", roles);
            attrs.put("sub", "patient-subject");
            when(session.getId()).thenReturn(sessionId);
            when(session.getAttributes()).thenReturn(attrs);
            when(session.getUri()).thenReturn(URI.create("ws://localhost/ws/patient-portal/careai"));
            when(session.isOpen()).thenReturn(true);
            when(session.isOpen()).thenReturn(true);
            org.mockito.Mockito.doAnswer(invocation -> {
                TextMessage message = invocation.getArgument(0);
                payloads.add(message.getPayload());
                return null;
            }).when(session).sendMessage(any(TextMessage.class));
        }

        private List<String> payloads() {
            return payloads;
        }
    }

    private static int indexOfPayload(List<String> payloads, String fragment) {
        for (int index = 0; index < payloads.size(); index++) {
            if (payloads.get(index).contains(fragment)) {
                return index;
            }
        }
        return -1;
    }
}
