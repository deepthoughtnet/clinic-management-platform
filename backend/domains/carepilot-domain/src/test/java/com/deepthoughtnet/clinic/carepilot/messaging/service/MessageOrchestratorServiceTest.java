package com.deepthoughtnet.clinic.carepilot.messaging.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.carepilot.messaging.exception.MessageDispatchException;
import com.deepthoughtnet.clinic.carepilot.messaging.resolver.MessagingProviderRegistry;
import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import com.deepthoughtnet.clinic.messaging.spi.MessageProvider;
import com.deepthoughtnet.clinic.messaging.spi.MessageRecipient;
import com.deepthoughtnet.clinic.messaging.spi.MessageRequest;
import com.deepthoughtnet.clinic.messaging.spi.MessageResult;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessageOrchestratorServiceTest {

    @Test
    void routesRequestThroughResolvedProvider() {
        MessagingProviderRegistry registry = mock(MessagingProviderRegistry.class);
        MessageProvider provider = mock(MessageProvider.class);
        MessageOrchestratorService service = new MessageOrchestratorService(registry, false);
        MessageRequest request = new MessageRequest(
                UUID.randomUUID(),
                MessageChannel.EMAIL,
                new MessageRecipient("p@example.com", null),
                "Subject",
                "Body",
                null,
                "cid",
                null,
                UUID.randomUUID(),
                Map.of()
        );
        MessageResult expected = new MessageResult(true, MessageDeliveryStatus.SENT, "email-provider", "external-1", null, null, OffsetDateTime.now());
        when(registry.resolve(MessageChannel.EMAIL)).thenReturn(provider);
        when(provider.send(request)).thenReturn(expected);

        MessageResult result = service.send(request);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void wrapsProviderFailureAsDispatchException() {
        MessagingProviderRegistry registry = mock(MessagingProviderRegistry.class);
        MessageProvider provider = mock(MessageProvider.class);
        MessageOrchestratorService service = new MessageOrchestratorService(registry, false);
        MessageRequest request = new MessageRequest(
                UUID.randomUUID(),
                MessageChannel.SMS,
                new MessageRecipient("9999999999", null),
                null,
                "Body",
                null,
                null,
                null,
                UUID.randomUUID(),
                Map.of()
        );
        when(registry.resolve(MessageChannel.SMS)).thenReturn(provider);
        when(provider.send(request)).thenThrow(new IllegalStateException("provider down"));

        assertThatThrownBy(() -> service.send(request))
                .isInstanceOf(MessageDispatchException.class)
                .hasMessageContaining("Failed to dispatch CarePilot message");
    }

    @Test
    void failFastRaisesExceptionWhenProviderIsNotConfigured() {
        MessagingProviderRegistry registry = mock(MessagingProviderRegistry.class);
        MessageProvider provider = mock(MessageProvider.class);
        MessageOrchestratorService service = new MessageOrchestratorService(registry, true);
        MessageRequest request = new MessageRequest(
                UUID.randomUUID(),
                MessageChannel.EMAIL,
                new MessageRecipient("p@example.com", null),
                "Subject",
                "Body",
                null,
                null,
                null,
                UUID.randomUUID(),
                Map.of()
        );
        when(registry.resolve(MessageChannel.EMAIL)).thenReturn(provider);
        when(provider.send(request)).thenReturn(new MessageResult(
                false, MessageDeliveryStatus.NOT_CONFIGURED, "email-provider", null, "NOT_CONFIGURED", "missing smtp", null
        ));

        assertThatThrownBy(() -> service.send(request))
                .isInstanceOf(MessageDispatchException.class)
                .hasMessageContaining("Failed to dispatch CarePilot message");
    }
}
