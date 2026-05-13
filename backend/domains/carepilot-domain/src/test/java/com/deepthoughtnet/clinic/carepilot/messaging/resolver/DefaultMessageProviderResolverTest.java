package com.deepthoughtnet.clinic.carepilot.messaging.resolver;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.carepilot.messaging.provider.NoOpMessageProvider;
import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import com.deepthoughtnet.clinic.messaging.spi.MessageProvider;
import com.deepthoughtnet.clinic.messaging.spi.MessageRecipient;
import com.deepthoughtnet.clinic.messaging.spi.MessageRequest;
import com.deepthoughtnet.clinic.messaging.spi.MessageResult;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultMessageProviderResolverTest {

    @Test
    void resolvesConcreteProviderWhenAvailable() {
        NoOpMessageProvider noOp = new NoOpMessageProvider();
        MessageProvider smsProvider = new MessageProvider() {
            @Override
            public boolean supports(MessageChannel channel) {
                return channel == MessageChannel.SMS;
            }

            @Override
            public MessageResult send(MessageRequest request) {
                return new MessageResult(true, MessageDeliveryStatus.SENT, "sms-provider", "x", null, null, OffsetDateTime.now());
            }

            @Override
            public String providerName() {
                return "sms-provider";
            }
        };

        MessagingProviderRegistry resolver = new MessagingProviderRegistry(List.of(noOp, smsProvider), noOp);

        assertThat(resolver.resolve(MessageChannel.SMS).providerName()).isEqualTo("sms-provider");
    }

    @Test
    void resolvesEmailSmsAndWhatsAppProviders() {
        NoOpMessageProvider noOp = new NoOpMessageProvider();
        MessageProvider emailProvider = provider("email-provider", MessageChannel.EMAIL);
        MessageProvider smsProvider = provider("sms-provider", MessageChannel.SMS);
        MessageProvider whatsappProvider = provider("wa-provider", MessageChannel.WHATSAPP);

        MessagingProviderRegistry resolver = new MessagingProviderRegistry(
                List.of(noOp, emailProvider, smsProvider, whatsappProvider),
                noOp
        );

        assertThat(resolver.resolve(MessageChannel.EMAIL).providerName()).isEqualTo("email-provider");
        assertThat(resolver.resolve(MessageChannel.SMS).providerName()).isEqualTo("sms-provider");
        assertThat(resolver.resolve(MessageChannel.WHATSAPP).providerName()).isEqualTo("wa-provider");
    }

    @Test
    void fallsBackToNoOpProviderWhenNoConcreteProviderSupportsChannel() {
        NoOpMessageProvider noOp = new NoOpMessageProvider();
        MessagingProviderRegistry resolver = new MessagingProviderRegistry(List.of(noOp), noOp);

        MessageProvider resolved = resolver.resolve(MessageChannel.WHATSAPP);
        MessageResult result = resolved.send(new MessageRequest(
                UUID.randomUUID(),
                MessageChannel.WHATSAPP,
                new MessageRecipient("recipient", null),
                null,
                "body",
                null,
                null,
                null,
                UUID.randomUUID(),
                Map.of()
        ));

        assertThat(resolved.providerName()).isEqualTo("carepilot-noop");
        assertThat(result.status()).isEqualTo(MessageDeliveryStatus.PROVIDER_NOT_AVAILABLE);
    }

    private MessageProvider provider(String providerName, MessageChannel supportedChannel) {
        return new MessageProvider() {
            @Override
            public boolean supports(MessageChannel channel) {
                return channel == supportedChannel;
            }

            @Override
            public MessageResult send(MessageRequest request) {
                return new MessageResult(true, MessageDeliveryStatus.SENT, providerName, "x", null, null, OffsetDateTime.now());
            }

            @Override
            public String providerName() {
                return providerName;
            }
        };
    }
}
