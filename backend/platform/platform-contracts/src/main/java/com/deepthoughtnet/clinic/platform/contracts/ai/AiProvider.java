package com.deepthoughtnet.clinic.platform.contracts.ai;

public interface AiProvider {
    String providerName();

    boolean supports(AiTaskType taskType);

    AiProviderResponse complete(AiProviderRequest request);

    default AiProviderStatus status() {
        return AiProviderStatus.AVAILABLE;
    }
}
