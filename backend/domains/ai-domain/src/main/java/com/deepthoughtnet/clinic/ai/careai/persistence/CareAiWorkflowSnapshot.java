package com.deepthoughtnet.clinic.ai.careai.persistence;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CareAiWorkflowSnapshot(
        CareAiWorkflowType workflowType,
        CareAiWorkflowState state,
        String contextJson,
        String lastQuestionKey,
        int repeatedQuestionCount,
        String eventType,
        String eventPayloadJson,
        UUID appointmentId,
        String pendingConfirmationType,
        String pendingConfirmationScopeKey,
        String pendingConfirmationPrompt,
        String pendingConfirmationPayloadJson,
        OffsetDateTime pendingConfirmationExpiresAt
) {
}
