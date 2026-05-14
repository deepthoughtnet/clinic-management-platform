package com.deepthoughtnet.clinic.voice.spi;

/** Provider-neutral voice call lifecycle statuses. */
public enum VoiceCallStatus {
    QUEUED,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    NO_ANSWER,
    BUSY,
    CANCELLED
}
