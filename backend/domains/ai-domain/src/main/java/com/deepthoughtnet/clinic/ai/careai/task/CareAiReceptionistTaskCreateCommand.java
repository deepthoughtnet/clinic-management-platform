package com.deepthoughtnet.clinic.ai.careai.task;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CareAiReceptionistTaskCreateCommand(
        UUID tenantId,
        UUID conversationId,
        UUID workflowId,
        UUID patientId,
        UUID leadId,
        UUID appointmentId,
        String channel,
        String reason,
        String latestUserMessage,
        String callbackTimePreference,
        OffsetDateTime callbackDueAt,
        String metadataJson
) {
}
