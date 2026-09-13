package com.deepthoughtnet.clinic.ai.orchestration.service.impl;

import com.deepthoughtnet.clinic.ai.orchestration.service.AiProviderRouter;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProvider;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderStatus;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class AiProviderRouterImpl implements AiProviderRouter {
    private static final Logger log = LoggerFactory.getLogger(AiProviderRouterImpl.class);
    private final List<AiProvider> providers;
    private final Map<String, Integer> providerOrder;
    private final Map<String, Integer> aivaV2ProviderOrder;
    private final Map<String, Integer> clinicalProviderOrder;
    private final boolean allowMockRuntime;

    @Autowired
    public AiProviderRouterImpl(List<AiProvider> providers,
                                @Value("${clinic.ai.provider-chain:${AI_LLM_PROVIDER_ORDER:${AI_PROVIDER_CHAIN:${VOICE_LLM_PROVIDER_ORDER:gemini,groq}}}}") String providerChain,
                                @Value("${clinic.ai.mock.allow-runtime:false}") boolean allowMockRuntime,
                                @Value("${clinic.ai.aiva-v2.provider-chain:${AIVA_V2_PROVIDER_CHAIN:SARVAM,GEMINI,GROQ}}") String aivaV2ProviderChain,
                                @Value("${clinic.ai.clinical.provider-chain:${CLINICAL_AI_PROVIDER_CHAIN:GEMINI,GROQ}}") String clinicalProviderChain) {
        this(providers, providerChain, allowMockRuntime, aivaV2ProviderChain, clinicalProviderChain, null);
    }

    public AiProviderRouterImpl(List<AiProvider> providers, String providerChain) {
        this.providers = providers == null ? List.of() : List.copyOf(providers);
        this.providerOrder = buildProviderOrder(providerChain);
        this.aivaV2ProviderOrder = buildProviderOrder("SARVAM,GEMINI,GROQ");
        this.clinicalProviderOrder = buildProviderOrder("GEMINI,GROQ");
        this.allowMockRuntime = true;
    }

    public AiProviderRouterImpl(List<AiProvider> providers, String providerChain, boolean allowMockRuntime) {
        this(providers, providerChain, allowMockRuntime, "SARVAM,GEMINI,GROQ", "GEMINI,GROQ", null);
    }

    AiProviderRouterImpl(List<AiProvider> providers, String providerChain, boolean allowMockRuntime,
                         String aivaV2ProviderChain, String clinicalProviderChain, Void ignored) {
        this.providers = providers == null ? List.of() : List.copyOf(providers);
        this.providerOrder = buildProviderOrder(providerChain);
        this.aivaV2ProviderOrder = buildProviderOrder(aivaV2ProviderChain);
        this.clinicalProviderOrder = buildProviderOrder(clinicalProviderChain);
        this.allowMockRuntime = allowMockRuntime;
        if (this.providerOrder.containsKey("MOCK") && !allowMockRuntime) {
            log.warn("AI_PROVIDER_CHAIN_GUARD mockExcluded=true reason=runtime_mock_not_allowed chain={}",
                    this.providerOrder.keySet());
        }
    }

    @Override
    public AiProvider resolve(AiTaskType taskType) {
        return resolveCandidates(taskType).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No AI provider available for task " + taskType));
    }

    @Override
    public List<AiProvider> resolveCandidates(AiTaskType taskType) {
        return isClinicalTask(taskType)
                ? resolveCandidates(taskType, clinicalProviderOrder)
                : resolveCandidates(taskType, providerOrder);
    }

    @Override
    public List<AiProvider> resolveCandidates(AiProductCode productCode, AiTaskType taskType, String useCaseCode) {
        if ("patient-portal-aiva-v2-decision".equalsIgnoreCase(useCaseCode)) {
            return resolveCandidates(taskType, aivaV2ProviderOrder, true);
        }
        if (taskType == AiTaskType.CLINICAL_REASONING || isClinicalTask(taskType)) {
            return resolveCandidates(taskType, clinicalProviderOrder, true);
        }
        return resolveCandidates(taskType, providerOrder);
    }

    private List<AiProvider> resolveCandidates(AiTaskType taskType, Map<String, Integer> order) {
        return resolveCandidates(taskType, order, false);
    }

    private List<AiProvider> resolveCandidates(AiTaskType taskType, Map<String, Integer> order,
                                               boolean restrictToPolicy) {
        List<AiProvider> candidates = providers.stream()
                .filter(provider -> provider.supports(taskType))
                .filter(provider -> provider.status() != AiProviderStatus.UNAVAILABLE)
                .filter(provider -> allowMockRuntime || !isMockProvider(provider.providerName()))
                .filter(provider -> !restrictToPolicy || order.containsKey(normalize(provider.providerName())))
                .sorted(Comparator.comparingInt(provider -> providerNameRank(order, provider.providerName())))
                .toList();
        return candidates;
    }

    private boolean isClinicalTask(AiTaskType taskType) {
        return taskType == AiTaskType.CLINICAL_DOCUMENT_EXTRACTION
                || taskType == AiTaskType.CONSULTATION_NOTE_STRUCTURING
                || taskType == AiTaskType.SYMPTOMS_DIAGNOSIS_DRAFT
                || taskType == AiTaskType.PRESCRIPTION_TEMPLATE_SUGGESTION
                || taskType == AiTaskType.PATIENT_INSTRUCTIONS_DRAFT
                || taskType == AiTaskType.ALLERGY_CONDITION_WARNING;
    }

    private boolean isMockProvider(String providerName) {
        return providerName != null && "MOCK".equalsIgnoreCase(providerName.trim());
    }

    private int providerNameRank(Map<String, Integer> order, String providerName) {
        if (providerName == null) {
            return Integer.MAX_VALUE;
        }
        return order.getOrDefault(normalize(providerName), Integer.MAX_VALUE);
    }

    private String normalize(String providerName) {
        return providerName == null ? "" : providerName.trim().toUpperCase(Locale.ROOT);
    }

    private Map<String, Integer> buildProviderOrder(String providerChain) {
        Map<String, Integer> order = new HashMap<>();
        if (providerChain == null || providerChain.isBlank()) {
            order.put("GEMINI", 0);
            order.put("GROQ", 1);
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
        return order;
    }
}
