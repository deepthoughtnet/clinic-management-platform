package com.deepthoughtnet.clinic.api.voice;

record VoiceTestResponse(
        String requestId,
        String transcript,
        String assistantText,
        String audioContentType,
        String audioBase64,
        VoiceProviderTrace providerTrace,
        java.util.List<VoiceDebugTraceEntry> voiceDebugTrace,
        VoiceWorkflowSummary workflowSummary
) {
    VoiceTestResponse(
            String requestId,
            String transcript,
            String assistantText,
            String audioContentType,
            String audioBase64,
            VoiceProviderTrace providerTrace,
            java.util.List<VoiceDebugTraceEntry> voiceDebugTrace
    ) {
        this(requestId, transcript, assistantText, audioContentType, audioBase64, providerTrace, voiceDebugTrace, null);
    }
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
        VoiceProviderTrace providerTrace,
        String sttConfiguredLanguage,
        String ttsConfiguredVoice,
        java.util.Map<String, String> ttsConfiguredVoices,
        boolean ttsHindiConfigured,
        boolean ttsFallbackVoiceEnabled
) {
}

record VoiceLiveStatusResponse(
        boolean websocketEnabled,
        String websocketPath,
        String authMode,
        String tenantMode,
        String vadMode,
        String vadProvider,
        int heartbeatIntervalMs,
        int staleAfterMs,
        int maxSessionDurationSeconds,
        int maxIdleSeconds,
        int maxTurnsPerSession,
        int maxAudioBytesPerTurn
) {
}

record VoiceWorkflowSummary(
        String mode,
        String intentState,
        String bookingWorkflowState,
        String language,
        String contactChannel,
        String patientId,
        String patientName,
        String patientPhone,
        String patientNumber,
        String patientMatchStatus,
        String doctorUserId,
        String doctorName,
        String doctorMatchStatus,
        String preferredDate,
        String preferredTimeWindow,
        String reason,
        java.util.List<String> missingFields,
        VoiceSuggestedSlot suggestedSlot,
        java.util.List<VoiceSuggestedSlot> slotSuggestions,
        boolean confirmationRequested,
        boolean bookingConfirmed,
        boolean booked,
        String bookedAppointmentId,
        boolean handoffRequired,
        String handoffReason,
        String nextPrompt,
        int unresolvedTurns,
        java.util.List<String> patientOptions,
        java.util.List<String> doctorOptions
) {
}

record VoiceSuggestedSlot(
        String doctorUserId,
        String doctorName,
        String appointmentDate,
        String slotTime,
        String slotEndTime
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
