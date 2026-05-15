package com.deepthoughtnet.clinic.realtime.voice.session;

/** Lifecycle states for realtime voice sessions. */
public enum VoiceSessionStatus {
    CREATED,
    CONNECTING,
    ACTIVE,
    PAUSED,
    ESCALATED,
    COMPLETED,
    FAILED
}
