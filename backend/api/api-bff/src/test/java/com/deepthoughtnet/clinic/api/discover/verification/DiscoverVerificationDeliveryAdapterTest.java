package com.deepthoughtnet.clinic.api.discover.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.carepilot.messaging.resolver.MessagingProviderRegistry;
import com.deepthoughtnet.clinic.discover.verification.DiscoverVerificationProperties;
import com.deepthoughtnet.clinic.discover.verification.VerificationChannel;
import com.deepthoughtnet.clinic.discover.verification.VerificationDeliveryRequest;
import com.deepthoughtnet.clinic.discover.verification.VerificationDeliveryResult;
import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import com.deepthoughtnet.clinic.messaging.spi.MessageProvider;
import com.deepthoughtnet.clinic.messaging.spi.MessageRecipient;
import com.deepthoughtnet.clinic.messaging.spi.MessageRequest;
import com.deepthoughtnet.clinic.messaging.spi.MessageResult;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiscoverVerificationDeliveryAdapterTest {
    @Mock
    private MessagingProviderRegistry providerRegistry;

    @Mock
    private MessageProvider provider;

    @Test
    void localModeExposesDevelopmentCodeWithoutCallingMessagingProviders() {
        DiscoverVerificationProperties properties = new DiscoverVerificationProperties();
        properties.setExposeDevelopmentCode(true);
        DiscoverVerificationDeliveryAdapter adapter = new DiscoverVerificationDeliveryAdapter(properties, providerRegistry);

        VerificationDeliveryResult result = adapter.deliver(new VerificationDeliveryRequest(
                null,
                null,
                com.deepthoughtnet.clinic.discover.verification.VerificationPurpose.PROVIDER_LOGIN_EMAIL,
                VerificationChannel.EMAIL,
                "discover.clinic.uat@jeevanam.test",
                "483921",
                "Subject",
                "Body",
                Map.of("purpose", "PROVIDER_LOGIN_EMAIL")
        ));

        assertThat(result.accepted()).isTrue();
        assertThat(result.developmentCode()).isEqualTo("483921");
        assertThat(result.message()).isEqualTo("Development verification code generated.");
        verifyNoMessagingCalls();
    }

    @Test
    void unavailableMessagingProviderMessagesAreSanitized() {
        DiscoverVerificationProperties properties = new DiscoverVerificationProperties();
        DiscoverVerificationDeliveryAdapter adapter = new DiscoverVerificationDeliveryAdapter(properties, providerRegistry);
        when(providerRegistry.resolve(MessageChannel.SMS)).thenReturn(provider);
        when(provider.send(any(MessageRequest.class))).thenReturn(new MessageResult(
                false,
                MessageDeliveryStatus.NOT_CONFIGURED,
                "carepilot-sms",
                null,
                "NOT_CONFIGURED",
                "clinic.carepilot.messaging.sms.enabled=false",
                null
        ));

        VerificationDeliveryResult result = adapter.deliver(new VerificationDeliveryRequest(
                null,
                null,
                com.deepthoughtnet.clinic.discover.verification.VerificationPurpose.PROVIDER_LOGIN_PHONE,
                VerificationChannel.SMS,
                "9876501201",
                "483921",
                "Subject",
                "Body",
                Map.of("purpose", "PROVIDER_LOGIN_PHONE")
        ));

        assertThat(result.accepted()).isFalse();
        assertThat(result.message()).isEqualTo("Verification service is temporarily unavailable. Please try again later.");
        assertThat(result.message()).doesNotContain("clinic.carepilot.messaging.sms.enabled=false");
        ArgumentCaptor<MessageRequest> requestCaptor = ArgumentCaptor.forClass(MessageRequest.class);
        verify(provider).send(requestCaptor.capture());
        assertThat(requestCaptor.getValue().recipient()).isEqualTo(new MessageRecipient("9876501201", null));
    }

    private void verifyNoMessagingCalls() {
        verify(providerRegistry, never()).resolve(any(MessageChannel.class));
        verify(provider, never()).send(any());
    }
}
