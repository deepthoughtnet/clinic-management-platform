package com.deepthoughtnet.clinic.api.ai.dto;

public record AiStatusResponse(
        boolean tenantModuleEnabled,
        boolean runtimeEnabled,
        String provider,
        boolean providerConfigured,
        boolean geminiEnabled,
        boolean geminiConfigured,
        boolean ocrEnabled,
        String ocrProvider,
        boolean userCanUseAi,
        String effectiveStatus,
        String message
) {
}
