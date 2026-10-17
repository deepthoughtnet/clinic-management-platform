package com.deepthoughtnet.clinic.carepilot.ai_call.provider;

import java.util.List;

/** Backend-only DotVoice media transport for deterministic reminder playback. */
public interface DotVoiceMediaStreamClient {
    default boolean isReady() { return false; }

    /** True while the vendor listener WebSocket is available for inbound calls. */
    default boolean isConnected() { return false; }

    default String connectionState() { return isConnected() ? "CONNECTED" : "DISCONNECTED"; }

    /** Starts the application-owned inbound listener. */
    default void startPersistentListener() { }

    /** Stops the application-owned inbound listener and its reconnect scheduling. */
    default void stopPersistentListener() { }

    /** Opens the vendor stream and waits for its application-level ready event. */
    default boolean prepare() { return isReady(); }

    default void abort() { }

    PlaybackResult play(String providerCallId, List<byte[]> mediaFrames);

    /** Starts prompt playback without waiting for the remote party to hang up. */
    default boolean startPlayback(String providerCallId, List<byte[]> mediaFrames) {
        return play(providerCallId, mediaFrames).success();
    }

    /** Requests a bot-initiated hangup for a live provider call. */
    default boolean hangup(String providerCallId) { return false; }

    /** Rejects an inbound call before the provider emits stream_start. */
    default boolean rejectCall(String providerCallId) { return false; }

    record PlaybackResult(boolean success, String status, String failureCategory, int framesSent, String cause) {}
}
