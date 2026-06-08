package com.deepthoughtnet.clinic.api.patientportal.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiConversationPersistenceService;
import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiConversationSessionSnapshot;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiConversationEntity;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiWorkflowEntity;
import com.deepthoughtnet.clinic.api.patientportal.careai.PatientPortalCareAiStateResponse;
import com.deepthoughtnet.clinic.api.patientportal.voice.PatientPortalVoiceAssistantService.PatientPortalVoiceTurnResponse;
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
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class PatientPortalVoiceWebSocketHandlerTest {
    private static final String TENANT_ID = UUID.randomUUID().toString();
    private static final String PATIENT_ID = UUID.randomUUID().toString();
    private static final String APP_USER_ID = UUID.randomUUID().toString();

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
        when(assistantService.processAudioTurn(any(), any(), any(), any())).thenReturn(
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
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"session.start\",\"language\":\"auto\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.chunk\",\"sequence\":1,\"totalChunks\":1,\"filename\":\"patient-careai.webm\",\"audioBase64Chunk\":\"" + audioBase64 + "\",\"contentType\":\"audio/webm\"}"));
        handler.handleForTest(fixture.session, new TextMessage("{\"type\":\"audio.end\",\"filename\":\"patient-careai.webm\",\"contentType\":\"audio/webm\",\"totalChunks\":1}"));

        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"session.started\""));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"transcript.final\"") && payload.contains("book an appointment"));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"assistant.text\"") && payload.contains("confirm the 10:30 slot"));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"assistant.audio.end\"") && payload.contains("\"contentType\":\"audio/wav\""));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"turn.complete\"") && payload.contains("\"currentIntent\":\"BOOK_APPOINTMENT\""));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"session.started\"") && payload.contains("\"voiceConfig\""));
        assertThat(fixture.payloads()).anyMatch(payload -> payload.contains("\"type\":\"turn.complete\"") && payload.contains("\"totalDurationMs\":260"));
        verify(assistantService).processAudioTurn(any(), any(), any(), any());
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
}
