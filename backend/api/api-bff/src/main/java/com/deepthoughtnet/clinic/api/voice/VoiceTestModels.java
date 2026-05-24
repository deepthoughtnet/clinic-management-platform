package com.deepthoughtnet.clinic.api.voice;

record VoiceTestResponse(
        String requestId,
        String transcript,
        String assistantText,
        String audioContentType,
        String audioBase64,
        VoiceProviderTrace providerTrace,
        java.util.List<VoiceDebugTraceEntry> voiceDebugTrace
) {
}

record VoiceSttDebugResponse(
        String requestId,
        String transcript,
        String sttProvider,
        java.util.List<VoiceDebugTraceEntry> voiceDebugTrace
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

record VoiceDebugTraceEntry(
        String stage,
        boolean ok,
        String provider,
        String from,
        String to,
        String filename,
        String contentType,
        Long sizeBytes,
        String url,
        String multipartField,
        Integer status,
        String bodyPreview,
        Long durationMs,
        Integer transcriptLength,
        String reason,
        String savedPath
) {
}
