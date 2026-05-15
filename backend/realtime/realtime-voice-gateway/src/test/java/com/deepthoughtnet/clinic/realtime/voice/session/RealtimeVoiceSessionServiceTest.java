package com.deepthoughtnet.clinic.realtime.voice.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.realtime.voice.db.VoiceSessionEntity;
import com.deepthoughtnet.clinic.realtime.voice.db.VoiceSessionEventEntity;
import com.deepthoughtnet.clinic.realtime.voice.db.VoiceSessionEventRepository;
import com.deepthoughtnet.clinic.realtime.voice.db.VoiceSessionRepository;
import com.deepthoughtnet.clinic.realtime.voice.db.VoiceTranscriptEntity;
import com.deepthoughtnet.clinic.realtime.voice.db.VoiceTranscriptRepository;
import com.deepthoughtnet.clinic.realtime.voice.escalation.VoiceEscalationDecider;
import com.deepthoughtnet.clinic.realtime.voice.events.VoiceSessionEventBus;
import com.deepthoughtnet.clinic.realtime.voice.metrics.RealtimeVoiceGatewayMetrics;
import com.deepthoughtnet.clinic.realtime.voice.config.VoiceGatewayProperties;
import com.deepthoughtnet.clinic.realtime.voice.orchestration.RealtimeConversationOrchestrator;
import com.deepthoughtnet.clinic.realtime.voice.orchestration.RollingConversationMemory;
import com.deepthoughtnet.clinic.stt.spi.MockSpeechToTextProvider;
import com.deepthoughtnet.clinic.tts.spi.MockTextToSpeechProvider;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RealtimeVoiceSessionServiceTest {
    private VoiceSessionRepository sessionRepository;
    private VoiceSessionEventRepository eventRepository;
    private VoiceTranscriptRepository transcriptRepository;
    private RealtimeConversationOrchestrator orchestrator;
    private RealtimeVoiceSessionService service;

    @BeforeEach
    void setUp() {
        sessionRepository = Mockito.mock(VoiceSessionRepository.class);
        eventRepository = Mockito.mock(VoiceSessionEventRepository.class);
        transcriptRepository = Mockito.mock(VoiceTranscriptRepository.class);
        orchestrator = Mockito.mock(RealtimeConversationOrchestrator.class);

        when(sessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(eventRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(transcriptRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(eventRepository.countBySessionId(any())).thenReturn(0L);

        service = new RealtimeVoiceSessionService(
                sessionRepository,
                eventRepository,
                transcriptRepository,
                List.of(new MockSpeechToTextProvider()),
                List.of(new MockTextToSpeechProvider()),
                orchestrator,
                new RollingConversationMemory(),
                new VoiceEscalationDecider(),
                new RealtimeVoiceGatewayMetrics(),
                new VoiceSessionEventBus(),
                new VoiceGatewayProperties()
        );
    }

    @Test
    void createSessionReturnsActiveSession() {
        UUID tenantId = UUID.randomUUID();
        var row = service.createSession(tenantId, VoiceSessionType.AI_RECEPTIONIST, null, null, "{}", "corr-1");
        assertNotNull(row.id());
        assertEquals(VoiceSessionStatus.ACTIVE, row.sessionStatus());
    }

    @Test
    void processTurnEscalatesOnEmergencyKeyword() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        VoiceSessionEntity session = VoiceSessionEntity.create(tenantId, VoiceSessionType.AI_RECEPTIONIST, null, null, "ai", "stt", "tts", "{}");
        when(sessionRepository.findByTenantIdAndId(tenantId, sessionId)).thenReturn(Optional.of(session));
        when(orchestrator.respond(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new RealtimeConversationOrchestrator.OrchestratorReply("I can help", 0.9d, "GENERIC", 10L));

        var result = service.processUserText(tenantId, sessionId, UUID.randomUUID(), "this is an emergency", "realtime.voice.ai-receptionist.v1", "{}", "corr-2");
        assertNotNull(result.escalationReason());
        assertEquals(VoiceSessionStatus.ESCALATED, session.getSessionStatus());
    }
}
