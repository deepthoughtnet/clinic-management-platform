package com.deepthoughtnet.clinic.voice.spi;

/** Strategy interface for pluggable outbound voice providers. */
public interface VoiceCallProvider {
    /** Logical provider name used for runtime visibility and analytics. */
    String providerName();

    /** True when provider is configured and ready for outbound requests. */
    boolean isReady();

    /** Places/simulates an outbound call. */
    VoiceCallResult placeCall(VoiceCallRequest request);
}
