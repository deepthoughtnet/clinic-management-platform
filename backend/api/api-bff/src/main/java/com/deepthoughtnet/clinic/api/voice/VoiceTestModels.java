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

