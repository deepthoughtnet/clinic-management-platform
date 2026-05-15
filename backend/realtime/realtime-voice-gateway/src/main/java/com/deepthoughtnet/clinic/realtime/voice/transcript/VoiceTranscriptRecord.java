package com.deepthoughtnet.clinic.realtime.voice.transcript;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Transcript response line. */
public record VoiceTranscriptRecord(UUID id, UUID sessionId, SpeakerType speakerType, String text,
                                    OffsetDateTime timestamp, Double confidence) {
}
