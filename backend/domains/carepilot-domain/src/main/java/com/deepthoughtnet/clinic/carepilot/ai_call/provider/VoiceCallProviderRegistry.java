package com.deepthoughtnet.clinic.carepilot.ai_call.provider;

import com.deepthoughtnet.clinic.voice.spi.VoiceCallProvider;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Resolves the active voice provider implementation. */
@Component
public class VoiceCallProviderRegistry {
    private final List<VoiceCallProvider> providers;
    private final String primaryProvider;
    private final String fallbackProvider;
    private final boolean failoverEnabled;

    public VoiceCallProviderRegistry(
            List<VoiceCallProvider> providers,
            @Value("${carepilot.ai-calls.provider.primary:mock}") String primaryProvider,
            @Value("${carepilot.ai-calls.provider.fallback:none}") String fallbackProvider,
            @Value("${carepilot.ai-calls.provider.failover-enabled:false}") boolean failoverEnabled
    ) {
        this.providers = providers;
        this.primaryProvider = primaryProvider;
        this.fallbackProvider = fallbackProvider;
        this.failoverEnabled = failoverEnabled;
    }

    /** Returns primary provider selection or null when no providers are available. */
    public VoiceCallProvider resolve() {
        return resolvePrimary();
    }

    /** Resolves configured primary provider when possible. */
    public VoiceCallProvider resolvePrimary() {
        if (providers == null || providers.isEmpty()) {
            return null;
        }
        return providers.stream()
                .filter(p -> matches(p.providerName(), primaryProvider))
                .filter(VoiceCallProvider::isReady)
                .findFirst()
                .orElseGet(() -> providers.stream().filter(VoiceCallProvider::isReady).findFirst().orElse(null));
    }

    /** Resolves configured fallback provider when enabled and available. */
    public VoiceCallProvider resolveFallback() {
        if (!failoverEnabled || "none".equalsIgnoreCase(fallbackProvider)) {
            return null;
        }
        return providers.stream()
                .filter(p -> matches(p.providerName(), fallbackProvider))
                .filter(VoiceCallProvider::isReady)
                .findFirst()
                .orElse(null);
    }

    public boolean failoverEnabled() {
        return failoverEnabled;
    }

    private boolean matches(String providerName, String configured) {
        if (providerName == null || configured == null) {
            return false;
        }
        String left = providerName.toLowerCase();
        String right = configured.toLowerCase();
        return left.equals(right) || left.contains(right);
    }
}
