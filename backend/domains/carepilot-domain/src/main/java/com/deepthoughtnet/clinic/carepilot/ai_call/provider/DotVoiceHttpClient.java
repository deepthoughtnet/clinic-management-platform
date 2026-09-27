package com.deepthoughtnet.clinic.carepilot.ai_call.provider;

/** Transport boundary for DotVoice, kept injectable for deterministic tests. */
public interface DotVoiceHttpClient {
    DotVoiceHttpResponse createCall(DotVoiceProperties properties, String destination, String script);
}
