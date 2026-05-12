package com.deepthoughtnet.clinic.carepilot.messaging.resolver;

import com.deepthoughtnet.clinic.carepilot.messaging.provider.NoOpMessageProvider;
import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.deepthoughtnet.clinic.messaging.spi.MessageProvider;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Registry that resolves the best provider for a requested channel.
 */
@Component
public class MessagingProviderRegistry {
    private final List<MessageProvider> providers;
    private final NoOpMessageProvider noOpMessageProvider;

    public MessagingProviderRegistry(List<MessageProvider> providers, NoOpMessageProvider noOpMessageProvider) {
        this.providers = providers;
        this.noOpMessageProvider = noOpMessageProvider;
    }

    /**
     * Resolves the first concrete provider supporting the requested channel.
     * Falls back to no-op provider to keep scheduler execution safe when no channel provider exists.
     */
    public MessageProvider resolve(MessageChannel channel) {
        return providers.stream()
                .filter(provider -> !provider.providerName().equals(noOpMessageProvider.providerName()))
                .filter(provider -> provider.supports(channel))
                .findFirst()
                .orElse(noOpMessageProvider);
    }
}
