package com.deepthoughtnet.clinic.carepilot.ai_call.model;

import java.time.OffsetDateTime;

/** Captured response independent of any appointment or reminder action. */
public record VoiceResponse(
        VoiceResponseIntent normalizedIntent,
        String rawTranscript,
        Double confidence,
        String language,
        int attemptNumber,
        String providerCallId,
        OffsetDateTime receivedAt
) {
    public VoiceResponse {
        normalizedIntent = normalizedIntent == null ? VoiceResponseIntent.NO_RESPONSE : normalizedIntent;
        rawTranscript = rawTranscript == null ? "" : rawTranscript;
        attemptNumber = Math.max(1, attemptNumber);
        receivedAt = receivedAt == null ? OffsetDateTime.now() : receivedAt;
    }
}
