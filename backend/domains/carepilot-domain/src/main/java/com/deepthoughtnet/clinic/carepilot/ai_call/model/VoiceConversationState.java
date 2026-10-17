package com.deepthoughtnet.clinic.carepilot.ai_call.model;

/** Explicit lifecycle for an in-flight provider voice interaction. */
public enum VoiceConversationState {
    PROMPTING,
    LISTENING,
    TRANSCRIBING,
    RESPONSE_CAPTURED,
    REPROMPTING,
    CLOSING,
    HANGUP_REQUESTED,
    ENDED
}
