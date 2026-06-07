package com.deepthoughtnet.clinic.ai.careai.persistence;

import java.util.UUID;

public record CareAiConversationTurnCommand(
        UUID tenantId,
        CareAiChannel channel,
        UUID patientId,
        UUID leadId,
        String externalSessionId,
        CareAiTransport transport,
        String activeInstanceId,
        String userMessage,
        String assistantMessage,
        String userIntent,
        String userEntitiesJson,
        String userMetadataJson,
        String assistantMetadataJson,
        String conversationSummary,
        CareAiConversationStatus conversationStatus,
        CareAiWorkflowSnapshot workflowSnapshot
) {
}
