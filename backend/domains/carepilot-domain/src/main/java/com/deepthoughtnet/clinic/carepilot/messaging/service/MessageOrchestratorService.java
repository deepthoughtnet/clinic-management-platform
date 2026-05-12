package com.deepthoughtnet.clinic.carepilot.messaging.service;

import com.deepthoughtnet.clinic.carepilot.messaging.exception.MessageDispatchException;
import com.deepthoughtnet.clinic.carepilot.messaging.resolver.MessagingProviderRegistry;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import com.deepthoughtnet.clinic.messaging.spi.MessageProvider;
import com.deepthoughtnet.clinic.messaging.spi.MessageRequest;
import com.deepthoughtnet.clinic.messaging.spi.MessageResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Tenant-aware orchestrator that routes CarePilot messages through SPI providers.
 */
@Service
public class MessageOrchestratorService {
    private final MessagingProviderRegistry providerRegistry;
    private final boolean failFast;

    public MessageOrchestratorService(
            MessagingProviderRegistry providerRegistry,
            @Value("${carepilot.provider.fail-fast:false}") boolean failFast
    ) {
        this.providerRegistry = providerRegistry;
        this.failFast = failFast;
    }

    public MessageResult send(MessageRequest request) {
        try {
            MessageProvider provider = providerRegistry.resolve(request.channel());
            MessageResult result = provider.send(request);
            if (failFast && (result.status() == MessageDeliveryStatus.PROVIDER_NOT_AVAILABLE || result.status() == MessageDeliveryStatus.NOT_CONFIGURED)) {
                throw new MessageDispatchException("CarePilot messaging provider unavailable for channel " + request.channel());
            }
            return result;
        } catch (RuntimeException ex) {
            throw new MessageDispatchException("Failed to dispatch CarePilot message", ex);
        }
    }
}
