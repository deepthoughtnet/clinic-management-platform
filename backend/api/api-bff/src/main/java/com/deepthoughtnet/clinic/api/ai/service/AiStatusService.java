package com.deepthoughtnet.clinic.api.ai.service;

import com.deepthoughtnet.clinic.api.ai.dto.AiStatusResponse;
import com.deepthoughtnet.clinic.api.security.PermissionChecker;
import com.deepthoughtnet.clinic.identity.service.TenantModuleEntitlementService;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProvider;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderStatus;
import com.deepthoughtnet.clinic.platform.core.module.ModuleKeys;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class AiStatusService {
    private final TenantModuleEntitlementService tenantModuleEntitlementService;
    private final PermissionChecker permissionChecker;
    private final List<AiProvider> providers;
    private final boolean clinicAiEnabled;
    private final String clinicAiProvider;
    private final String clinicAiProviderChain;
    private final boolean geminiEnabled;
    private final String geminiApiKey;
    private final boolean ocrEnabled;
    private final String ocrProvider;
    private final Environment environment;

    public AiStatusService(
            TenantModuleEntitlementService tenantModuleEntitlementService,
            PermissionChecker permissionChecker,
            List<AiProvider> providers,
            @Value("${clinic.ai.enabled:false}") boolean clinicAiEnabled,
            @Value("${clinic.ai.provider:DISABLED}") String clinicAiProvider,
            @Value("${clinic.ai.provider-chain:GEMINI,GROQ,MOCK}") String clinicAiProviderChain,
            @Value("${clinic.ai.gemini.enabled:false}") boolean geminiEnabled,
            @Value("${clinic.ai.gemini.api-key:}") String geminiApiKey,
            @Value("${clinic.ocr.enabled:true}") boolean ocrEnabled,
            @Value("${clinic.ocr.provider:TESSERACT}") String ocrProvider,
            Environment environment
    ) {
        this.tenantModuleEntitlementService = tenantModuleEntitlementService;
        this.permissionChecker = permissionChecker;
        this.providers = providers == null ? List.of() : List.copyOf(providers);
        this.clinicAiEnabled = clinicAiEnabled;
        this.clinicAiProvider = normalize(clinicAiProvider, "DISABLED");
        this.clinicAiProviderChain = clinicAiProviderChain == null ? "" : clinicAiProviderChain;
        this.geminiEnabled = geminiEnabled;
        this.geminiApiKey = geminiApiKey;
        this.ocrEnabled = ocrEnabled;
        this.ocrProvider = normalize(ocrProvider, "TESSERACT");
        this.environment = environment;
    }

    @PostConstruct
    void logConfig() {
        List<String> providerStates = providers.stream()
                .map(provider -> normalize(provider.providerName(), "UNKNOWN") + ":" + provider.status())
                .toList();
        boolean geminiKeyPresent = geminiApiKey != null && !geminiApiKey.isBlank();
        String geminiKeyPrefix = geminiKeyPresent ? geminiApiKey.substring(0, Math.min(4, geminiApiKey.length())) : "-";
        org.slf4j.LoggerFactory.getLogger(AiStatusService.class).info(
                "AI config activeProfiles={} runtimeEnabled={} activeProvider={} providerChain={} geminiEnabled={} geminiKeyPresent={} keyPrefix={} providerBeans={}",
                String.join(",", environment.getActiveProfiles()),
                clinicAiEnabled,
                activeProviderName(),
                clinicAiProviderChain,
                geminiEnabled,
                geminiKeyPresent,
                geminiKeyPrefix,
                providerStates
        );
    }

    public AiStatusResponse status(UUID tenantId) {
        boolean tenantModuleEnabled = tenantModuleEntitlementService.isModuleEnabled(tenantId, ModuleKeys.AI_COPILOT);
        boolean userCanUseAi = permissionChecker.hasAnyPermission("ai_copilot.run", "ai_copilot.clinic.run");
        boolean runtimeEnabled = clinicAiEnabled;
        String provider = activeProviderName();
        boolean providerConfigured = runtimeEnabled && hasAnyProviderAvailable();
        boolean geminiConfigured = geminiEnabled && geminiApiKey != null && !geminiApiKey.isBlank();

        String effectiveStatus = "READY";
        String message = "AI assistance is ready.";
        if (!tenantModuleEnabled) {
            effectiveStatus = "MODULE_DISABLED";
            message = "AI module is not enabled for this clinic.";
        } else if (!userCanUseAi) {
            effectiveStatus = "ACCESS_DENIED";
            message = "You do not have permission to use AI assistance.";
        } else if (!runtimeEnabled || "DISABLED".equals(provider)) {
            effectiveStatus = "RUNTIME_DISABLED";
            message = "AI assistance is disabled in runtime configuration.";
        } else if (!providerConfigured) {
            effectiveStatus = "PROVIDER_NOT_CONFIGURED";
            message = "AI module is enabled for this clinic, but AI provider is not configured.";
        } else {
            message = "AI assistance is ready.";
        }

        return new AiStatusResponse(
                tenantModuleEnabled,
                runtimeEnabled,
                provider,
                providerConfigured,
                geminiEnabled,
                geminiConfigured,
                ocrEnabled,
                ocrProvider,
                userCanUseAi,
                effectiveStatus,
                message
        );
    }

    public void requireProviderReady(UUID tenantId) {
        AiStatusResponse status = status(tenantId);
        if (!status.tenantModuleEnabled()) {
            throw new IllegalStateException("AI module is not enabled for this clinic.");
        }
        if (!status.userCanUseAi()) {
            throw new IllegalStateException("You do not have permission to use AI assistance.");
        }
        if (!status.runtimeEnabled() || "DISABLED".equals(status.provider())) {
            throw new IllegalStateException("AI assistance is not enabled or configured for this clinic.");
        }
        if (!status.providerConfigured()) {
            throw new IllegalStateException("AI module is enabled for this clinic, but AI provider is not configured.");
        }
    }

    private boolean providerAvailable(String providerName) {
        return providers.stream()
                .filter(provider -> providerName.equalsIgnoreCase(normalize(provider.providerName(), "")))
                .anyMatch(provider -> provider.status() != AiProviderStatus.UNAVAILABLE);
    }

    private String activeProviderName() {
        List<String> chain = parseProviderChain();
        for (String providerName : chain) {
            if (providerAvailable(providerName)) {
                return providerName;
            }
        }
        if (providerAvailable(clinicAiProvider)) {
            return clinicAiProvider;
        }
        return clinicAiProvider;
    }

    private boolean hasAnyProviderAvailable() {
        if (parseProviderChain().stream().anyMatch(this::providerAvailable)) {
            return true;
        }
        return providerAvailable(clinicAiProvider);
    }

    private List<String> parseProviderChain() {
        if (clinicAiProviderChain == null || clinicAiProviderChain.isBlank()) {
            return List.of("GEMINI", "GROQ", "MOCK");
        }
        return java.util.Arrays.stream(clinicAiProviderChain.split(","))
                .map(value -> normalize(value, ""))
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
