package com.deepthoughtnet.clinic.carepilot.messaging.resolver;

import com.deepthoughtnet.clinic.carepilot.messaging.provider.NoOpMessageProvider;
import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.deepthoughtnet.clinic.messaging.spi.MessageProvider;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Registry that resolves the best provider for a requested channel.
 */
@Component
public class MessagingProviderRegistry {
    private final List<MessageProvider> providers;
    private final NoOpMessageProvider noOpMessageProvider;
    private final String configuredEmailProvider;

    /** Backward-compatible constructor for domain tests and non-Spring callers. */
    public MessagingProviderRegistry(List<MessageProvider> providers, NoOpMessageProvider noOpMessageProvider) {
        this(providers, noOpMessageProvider, "");
    }

    @Autowired
    public MessagingProviderRegistry(
            List<MessageProvider> providers,
            NoOpMessageProvider noOpMessageProvider,
            @Value("${clinic.carepilot.messaging.email.provider:}") String configuredEmailProvider
    ) {
        this.providers = providers;
        this.noOpMessageProvider = noOpMessageProvider;
        this.configuredEmailProvider = configuredEmailProvider == null ? "" : configuredEmailProvider.trim();
    }

    /**
     * Resolves the first concrete provider supporting the requested channel.
     * Falls back to no-op provider to keep scheduler execution safe when no channel provider exists.
     */
    public MessageProvider resolve(MessageChannel channel) {
        if (channel == MessageChannel.EMAIL && isExplicitEmailProviderConfigured()) {
            return providers.stream()
                    .filter(provider -> !provider.providerName().equals(noOpMessageProvider.providerName()))
                    .filter(provider -> provider.supports(channel))
                    .filter(provider -> configuredEmailProvider.equalsIgnoreCase(provider.providerName()))
                    .findFirst()
                    .orElse(noOpMessageProvider);
        }
        return providers.stream()
                .filter(provider -> !provider.providerName().equals(noOpMessageProvider.providerName()))
                .filter(provider -> provider.supports(channel))
                .findFirst()
                .orElse(noOpMessageProvider);
    }

    private boolean isExplicitEmailProviderConfigured() {
        return !configuredEmailProvider.isBlank() && !"disabled".equalsIgnoreCase(configuredEmailProvider);
    }
}
