package com.deepthoughtnet.clinic.voice.spi;

import java.time.OffsetDateTime;

/** Provider-neutral result for one outbound call execution attempt. */
public record VoiceCallResult(
        VoiceCallStatus status,
        String providerName,
        String providerCallId,
        String failureReason,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        VoiceCallTranscript transcript
) {
    public VoiceCallResult {
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
    }
}
