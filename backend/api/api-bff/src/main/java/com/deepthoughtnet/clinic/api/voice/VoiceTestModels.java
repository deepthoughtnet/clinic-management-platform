package com.deepthoughtnet.clinic.api.voice;

record VoiceTestResponse(
        String requestId,
        String transcript,
        String assistantText,
        String audioContentType,
        String audioBase64,
        VoiceProviderTrace providerTrace
) {
}

record VoiceProviderTrace(
        String sttProvider,
        String llmProvider,
        String ttsProvider
) {
}

record VoiceStatusResponse(
        boolean enabled,
        VoiceServiceStatus stt,
        VoiceServiceStatus tts,
        VoiceProviderTrace providerTrace
) {
}

record VoiceLiveStatusResponse(
        boolean websocketEnabled,
        String websocketPath,
        String authMode,
        String tenantMode
) {
}

record VoiceServiceStatus(
        String provider,
        boolean reachable,
        boolean ready,
        String message
) {
}
