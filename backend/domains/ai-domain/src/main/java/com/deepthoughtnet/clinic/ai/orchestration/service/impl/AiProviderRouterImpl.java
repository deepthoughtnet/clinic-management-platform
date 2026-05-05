package com.deepthoughtnet.clinic.ai.orchestration.service.impl;

import com.deepthoughtnet.clinic.ai.orchestration.service.AiProviderRouter;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProvider;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderStatus;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AiProviderRouterImpl implements AiProviderRouter {
    private static final String DEFAULT_PROVIDER_NAME = "GEMINI";
    private static final String FALLBACK_PROVIDER_NAME = "GROQ";

    private final List<AiProvider> providers;

    public AiProviderRouterImpl(List<AiProvider> providers) {
        this.providers = providers == null ? List.of() : List.copyOf(providers);
    }

    @Override
    public AiProvider resolve(AiTaskType taskType) {
        return resolveCandidates(taskType).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No AI provider available for task " + taskType));
    }

    @Override
    public List<AiProvider> resolveCandidates(AiTaskType taskType) {
        return providers.stream()
                .filter(provider -> provider.supports(taskType))
                .filter(provider -> provider.status() != AiProviderStatus.UNAVAILABLE)
                .sorted(Comparator.comparingInt(provider -> providerNameRank(provider.providerName())))
                .toList();
    }

    private int providerNameRank(String providerName) {
        if (providerName == null) {
            return Integer.MAX_VALUE;
        }
        if (DEFAULT_PROVIDER_NAME.equalsIgnoreCase(providerName.trim())) {
            return 0;
        }
        if (FALLBACK_PROVIDER_NAME.equalsIgnoreCase(providerName.trim())) {
            return 1;
        }
        return 2;
    }
}
