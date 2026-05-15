package com.deepthoughtnet.clinic.realtime.voice.orchestration;

import com.deepthoughtnet.clinic.ai.orchestration.service.AiOrchestrationService;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import com.deepthoughtnet.clinic.realtime.voice.db.VoiceSessionEntity;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Realtime LLM orchestration layer that delegates to the shared AI orchestration platform.
 */
@Service
public class RealtimeConversationOrchestrator {
    private final AiOrchestrationService aiOrchestrationService;

    public RealtimeConversationOrchestrator(AiOrchestrationService aiOrchestrationService) {
        this.aiOrchestrationService = aiOrchestrationService;
    }

    public OrchestratorReply respond(VoiceSessionEntity session, UUID actorUserId, String promptTemplateCode,
                                     String userMessage, String rollingContext, String patientContextJson,
                                     String correlationId) {
        var response = aiOrchestrationService.complete(new AiOrchestrationRequest(
                AiProductCode.GENERIC,
                session.getTenantId(),
                actorUserId,
                AiTaskType.GENERIC_COPILOT,
                promptTemplateCode,
                Map.of(
                        "userMessage", userMessage,
                        "conversationContext", rollingContext,
                        "patientContext", patientContextJson == null ? "{}" : patientContextJson,
                        "sessionType", session.getSessionType().name()
                ),
                java.util.List.of(),
                512,
                0.3d,
                correlationId,
                "realtime-voice"
        ));

        return new OrchestratorReply(
                response.outputText() == null ? "I could not generate a response." : response.outputText(),
                response.confidence() == null ? null : response.confidence().doubleValue(),
                response.provider(),
                response.latencyMs() == null ? 0L : response.latencyMs()
        );
    }

    public record OrchestratorReply(String aiText, Double confidence, String provider, long latencyMs) {
    }
}
