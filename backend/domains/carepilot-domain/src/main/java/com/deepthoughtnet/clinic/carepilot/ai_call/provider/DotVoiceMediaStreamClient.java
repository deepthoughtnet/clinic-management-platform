package com.deepthoughtnet.clinic.carepilot.ai_call.provider;

import java.util.List;

/** Backend-only DotVoice media transport for deterministic reminder playback. */
public interface DotVoiceMediaStreamClient {
    default boolean isReady() { return false; }

    /** Opens the vendor stream and waits for its application-level ready event. */
    default boolean prepare() { return isReady(); }

    default void abort() { }

    PlaybackResult play(String providerCallId, List<byte[]> mediaFrames);

    record PlaybackResult(boolean success, String status, String failureCategory, int framesSent, String cause) {}
}
