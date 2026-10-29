package com.deepthoughtnet.clinic.carepilot.ai_call.provider;

/** Adapter boundary for gateway-to-platform caller audio and lifecycle events. */
public interface DotVoiceInboundMediaHandler {
    default void onIncomingCall(String providerCallId, String callerPhoneNumber, String didNumber) {}
    default void onStreamStart(String providerCallId) {}
    default void onStreamStartCorrelation(String providerCallId, boolean activePlaybackMatched, boolean pendingStartPresent) {}
    default void onMedia(String providerCallId, byte[] muLawAudio) {}
    default void onPlaybackStarted(String providerCallId) {}
    default void onPlaybackComplete(String providerCallId) {}
    /** Called when response preparation fails before media is sent to the call. */
    default void onPlaybackPreparationFailed(String providerCallId) {}
    default void onStreamStop(String providerCallId, String cause) {}
}
