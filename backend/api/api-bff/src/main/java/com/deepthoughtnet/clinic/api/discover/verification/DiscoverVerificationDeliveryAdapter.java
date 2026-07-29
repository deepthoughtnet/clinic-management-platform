package com.deepthoughtnet.clinic.api.discover.verification;

import com.deepthoughtnet.clinic.carepilot.messaging.resolver.MessagingProviderRegistry;
import com.deepthoughtnet.clinic.discover.verification.DiscoverVerificationProperties;
import com.deepthoughtnet.clinic.discover.verification.VerificationChannel;
import com.deepthoughtnet.clinic.discover.verification.VerificationDeliveryPort;
import com.deepthoughtnet.clinic.discover.verification.VerificationDeliveryRequest;
import com.deepthoughtnet.clinic.discover.verification.VerificationDeliveryResult;
import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import com.deepthoughtnet.clinic.messaging.spi.MessageProvider;
import com.deepthoughtnet.clinic.messaging.spi.MessageRecipient;
import com.deepthoughtnet.clinic.messaging.spi.MessageRequest;
import com.deepthoughtnet.clinic.messaging.spi.MessageResult;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DiscoverVerificationDeliveryAdapter implements VerificationDeliveryPort {
    private final DiscoverVerificationProperties properties;
    private final MessagingProviderRegistry providerRegistry;

    public DiscoverVerificationDeliveryAdapter(
            DiscoverVerificationProperties properties,
            MessagingProviderRegistry providerRegistry
    ) {
        this.properties = properties;
        this.providerRegistry = providerRegistry;
    }

    @Override
    public VerificationDeliveryResult deliver(VerificationDeliveryRequest request) {
        if (properties.isExposeDevelopmentCode()) {
            return VerificationDeliveryResult.accepted(
                    "discover-verification-local",
                    "local-" + UUID.randomUUID(),
                    request.code(),
                    "Development verification code generated."
            );
        }

        MessageChannel channel = toMessageChannel(request.channel());
        MessageProvider provider = providerRegistry.resolve(channel);
        MessageRequest messageRequest = new MessageRequest(
                properties.getDeliveryTenantId(),
                channel,
                new MessageRecipient(request.normalizedRecipient(), null),
                request.subject(),
                request.body(),
                null,
                request.purpose().name(),
                null,
                null,
                request.metadata() == null ? Map.of() : request.metadata()
        );
        MessageResult result = provider.send(messageRequest);
        if (result.success() && (result.status() == MessageDeliveryStatus.SENT || result.status() == MessageDeliveryStatus.QUEUED || result.status() == MessageDeliveryStatus.DELIVERED)) {
            return VerificationDeliveryResult.accepted(
                    result.providerName(),
                    result.providerMessageId(),
                    null,
                    "Verification code sent."
            );
        }
        String message = StringUtils.hasText(result.errorMessage()) ? result.errorMessage() : "Verification provider is not available.";
        return VerificationDeliveryResult.unavailable(result.providerName(), message);
    }

    private MessageChannel toMessageChannel(VerificationChannel channel) {
        return switch (channel) {
            case EMAIL -> MessageChannel.EMAIL;
            case SMS -> MessageChannel.SMS;
        };
    }
}
