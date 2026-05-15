package com.deepthoughtnet.clinic.realtime.voice.session;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Read model for voice session API/UI consumers. */
public record VoiceSessionRecord(
        UUID id,
        UUID tenantId,
        VoiceSessionType sessionType,
        VoiceSessionStatus sessionStatus,
        UUID patientId,
        UUID leadId,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        boolean escalationRequired,
        String escalationReason,
        UUID assignedHumanUserId,
        String aiProvider,
        String sttProvider,
        String ttsProvider,
        String metadataJson
) {
}
