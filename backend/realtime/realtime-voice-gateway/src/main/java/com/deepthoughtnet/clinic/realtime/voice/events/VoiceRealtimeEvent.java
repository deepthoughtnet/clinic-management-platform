package com.deepthoughtnet.clinic.realtime.voice.events;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Realtime event payload forwarded to websocket subscribers. */
public record VoiceRealtimeEvent(UUID sessionId, VoiceSessionEventType type, String message,
                                 long sequenceNumber, OffsetDateTime timestamp) {
}
