package com.deepthoughtnet.clinic.ai.orchestration.service.impl;

import com.deepthoughtnet.clinic.ai.orchestration.service.AiProviderRouter;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProvider;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderStatus;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

@Service
public class AiProviderRouterImpl implements AiProviderRouter {
    private final List<AiProvider> providers;
    private final Map<String, Integer> providerOrder;

    public AiProviderRouterImpl(List<AiProvider> providers,
                                @Value("${clinic.ai.provider-chain:${AI_LLM_PROVIDER_ORDER:${AI_PROVIDER_CHAIN:${VOICE_LLM_PROVIDER_ORDER:gemini,groq,mock}}}}") String providerChain) {
        this.providers = providers == null ? List.of() : List.copyOf(providers);
        this.providerOrder = buildProviderOrder(providerChain);
    }

    @Override
    public AiProvider resolve(AiTaskType taskType) {
        return resolveCandidates(taskType).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No AI provider available for task " + taskType));
    }

    @Override
    public List<AiProvider> resolveCandidates(AiTaskType taskType) {
        List<AiProvider> candidates = providers.stream()
                .filter(provider -> provider.supports(taskType))
                .filter(provider -> provider.status() != AiProviderStatus.UNAVAILABLE)
                .sorted(Comparator.comparingInt(provider -> providerNameRank(taskType, provider.providerName())))
                .toList();
        return candidates;
    }

    private boolean isMockProvider(String providerName) {
        return providerName != null && "MOCK".equalsIgnoreCase(providerName.trim());
    }

    private int providerNameRank(AiTaskType taskType, String providerName) {
        if (providerName == null) {
            return Integer.MAX_VALUE;
        }
        return providerOrder.getOrDefault(providerName.trim().toUpperCase(Locale.ROOT), Integer.MAX_VALUE);
    }

    private Map<String, Integer> buildProviderOrder(String providerChain) {
        Map<String, Integer> order = new HashMap<>();
        if (providerChain == null || providerChain.isBlank()) {
            order.put("GEMINI", 0);
            order.put("GROQ", 1);
            order.put("MOCK", 2);
            return order;
        }
        int index = 0;
        for (String entry : providerChain.split(",")) {
            String normalized = entry == null ? "" : entry.trim().toUpperCase(Locale.ROOT);
            if (normalized.isBlank() || order.containsKey(normalized)) {
                continue;
            }
            order.put(normalized, index++);
        }
        if (!order.containsKey("GEMINI")) {
            order.put("GEMINI", index++);
        }
        if (!order.containsKey("GROQ")) {
            order.put("GROQ", index++);
        }
        if (!order.containsKey("MOCK")) {
            order.put("MOCK", index);
        }
        return order;
    }
}
