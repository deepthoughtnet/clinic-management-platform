package com.deepthoughtnet.clinic.realtime.voice.events;

/** Timeline events emitted during realtime sessions. */
public enum VoiceSessionEventType {
    SESSION_STARTED,
    AUDIO_RECEIVED,
    STT_TRANSCRIPT,
    AI_RESPONSE,
    TTS_GENERATED,
    INTERRUPTION,
    HUMAN_HANDOFF,
    RECEPTIONIST_EXTRACTION,
    ESCALATION,
    SESSION_COMPLETED,
    SESSION_FAILED
}
