package com.deepthoughtnet.clinic.api.carepilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.carepilot.dto.MessagingDtos.ProviderReadinessStatus;
import com.deepthoughtnet.clinic.carepilot.messaging.resolver.MessagingProviderRegistry;
import com.deepthoughtnet.clinic.messaging.email.CarePilotEmailMessagingProperties;
import com.deepthoughtnet.clinic.messaging.sms.CarePilotSmsMessagingProperties;
import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.deepthoughtnet.clinic.messaging.spi.MessageProvider;
import com.deepthoughtnet.clinic.messaging.spi.MessageRequest;
import com.deepthoughtnet.clinic.messaging.spi.MessageResult;
import com.deepthoughtnet.clinic.messaging.whatsapp.CarePilotWhatsAppMessagingProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CarePilotMessagingStatusServiceTest {

    @Test
    void returnsStatusesForEmailSmsAndWhatsapp() {
        MessagingProviderRegistry registry = mock(MessagingProviderRegistry.class);
        when(registry.resolve(MessageChannel.EMAIL)).thenReturn(provider("carepilot-email", MessageChannel.EMAIL));
        when(registry.resolve(MessageChannel.SMS)).thenReturn(provider("carepilot-sms-foundation", MessageChannel.SMS));
        when(registry.resolve(MessageChannel.WHATSAPP)).thenReturn(provider("carepilot-whatsapp-foundation", MessageChannel.WHATSAPP));

        CarePilotEmailMessagingProperties email = new CarePilotEmailMessagingProperties();
        email.setEnabled(true);
        email.setFromAddress("carepilot@example.com");

        CarePilotSmsMessagingProperties sms = new CarePilotSmsMessagingProperties();
        sms.setEnabled(false);
        sms.setProvider("generic-http");
        sms.setApiUrl("https://sms.example/send");
        sms.setApiKey("k");
        sms.setFromNumber("CLINIC");

        CarePilotWhatsAppMessagingProperties whatsApp = new CarePilotWhatsAppMessagingProperties();
        whatsApp.setEnabled(false);

        CarePilotMessagingStatusService service = new CarePilotMessagingStatusService(
                registry, email, sms, whatsApp, "smtp", true, "smtp.example.com"
        );

        var statuses = service.providerStatuses();

        assertThat(statuses).hasSize(3);
        assertThat(statuses).extracting(s -> s.channel().name()).containsExactly("EMAIL", "SMS", "WHATSAPP");
        assertThat(statuses.get(0).status()).isEqualTo(ProviderReadinessStatus.READY);
        assertThat(statuses.get(1).status()).isEqualTo(ProviderReadinessStatus.DISABLED);
        assertThat(statuses.get(2).status()).isEqualTo(ProviderReadinessStatus.DISABLED);
    }

    @Test
    void enabledButMissingSmsConfigReturnsNotConfigured() {
        MessagingProviderRegistry registry = mock(MessagingProviderRegistry.class);
        when(registry.resolve(MessageChannel.EMAIL)).thenReturn(provider("carepilot-email", MessageChannel.EMAIL));
        when(registry.resolve(MessageChannel.SMS)).thenReturn(provider("SMS_NOT_CONFIGURED", MessageChannel.SMS));
        when(registry.resolve(MessageChannel.WHATSAPP)).thenReturn(provider("WHATSAPP_NOT_CONFIGURED", MessageChannel.WHATSAPP));

        CarePilotEmailMessagingProperties email = new CarePilotEmailMessagingProperties();
        CarePilotSmsMessagingProperties sms = new CarePilotSmsMessagingProperties();
        sms.setEnabled(true);
        sms.setProvider("disabled");
        sms.setFromNumber("");
        CarePilotWhatsAppMessagingProperties whatsApp = new CarePilotWhatsAppMessagingProperties();

        CarePilotMessagingStatusService service = new CarePilotMessagingStatusService(
                registry, email, sms, whatsApp, "logging", false, ""
        );

        var smsStatus = service.providerStatuses().stream()
                .filter(s -> s.channel() == MessageChannel.SMS)
                .findFirst()
                .orElseThrow();

        assertThat(smsStatus.status()).isEqualTo(ProviderReadinessStatus.NOT_CONFIGURED);
        assertThat(smsStatus.missingConfigurationKeys()).contains(
                "carepilot.messaging.sms.provider",
                "carepilot.messaging.sms.from-number or carepilot.messaging.sms.sender-id"
        );
        assertThat(smsStatus.message()).contains("incomplete");
    }

    @Test
    void enabledButMissingWhatsAppConfigReturnsNotConfigured() {
        MessagingProviderRegistry registry = mock(MessagingProviderRegistry.class);
        when(registry.resolve(MessageChannel.EMAIL)).thenReturn(provider("carepilot-email", MessageChannel.EMAIL));
        when(registry.resolve(MessageChannel.SMS)).thenReturn(provider("carepilot-sms-generic-http", MessageChannel.SMS));
        when(registry.resolve(MessageChannel.WHATSAPP)).thenReturn(provider("carepilot-whatsapp-meta-cloud-api", MessageChannel.WHATSAPP));

        CarePilotEmailMessagingProperties email = new CarePilotEmailMessagingProperties();
        CarePilotSmsMessagingProperties sms = new CarePilotSmsMessagingProperties();
        CarePilotWhatsAppMessagingProperties whatsApp = new CarePilotWhatsAppMessagingProperties();
        whatsApp.setEnabled(true);
        whatsApp.setProvider("meta-cloud-api");
        whatsApp.setApiUrl("");
        whatsApp.setAccessToken("");
        whatsApp.setPhoneNumberId("");

        CarePilotMessagingStatusService service = new CarePilotMessagingStatusService(
                registry, email, sms, whatsApp, "logging", false, ""
        );

        var whatsAppStatus = service.providerStatuses().stream()
                .filter(s -> s.channel() == MessageChannel.WHATSAPP)
                .findFirst()
                .orElseThrow();

        assertThat(whatsAppStatus.status()).isEqualTo(ProviderReadinessStatus.NOT_CONFIGURED);
        assertThat(whatsAppStatus.missingConfigurationKeys()).contains(
                "carepilot.messaging.whatsapp.api-url",
                "carepilot.messaging.whatsapp.access-token",
                "carepilot.messaging.whatsapp.phone-number-id"
        );
        assertThat(whatsAppStatus.message()).contains("incomplete");
    }

    @Test
    void responseDoesNotExposeSecretValues() {
        MessagingProviderRegistry registry = mock(MessagingProviderRegistry.class);
        when(registry.resolve(MessageChannel.EMAIL)).thenReturn(provider("carepilot-email", MessageChannel.EMAIL));
        when(registry.resolve(MessageChannel.SMS)).thenReturn(provider("SMS_NOT_CONFIGURED", MessageChannel.SMS));
        when(registry.resolve(MessageChannel.WHATSAPP)).thenReturn(provider("WHATSAPP_NOT_CONFIGURED", MessageChannel.WHATSAPP));

        CarePilotEmailMessagingProperties email = new CarePilotEmailMessagingProperties();
        email.setEnabled(true);
        email.setFromAddress("carepilot@example.com");
        CarePilotSmsMessagingProperties sms = new CarePilotSmsMessagingProperties();
        sms.setEnabled(true);
        sms.setApiKey("super-secret-key");
        CarePilotWhatsAppMessagingProperties whatsApp = new CarePilotWhatsAppMessagingProperties();

        CarePilotMessagingStatusService service = new CarePilotMessagingStatusService(
                registry, email, sms, whatsApp, "smtp", true, "smtp.example.com"
        );

        var payload = service.providerStatuses().toString();
        assertThat(payload).doesNotContain("super-secret-key");
    }

    @Test
    void smsConfiguredGenericHttpReturnsReady() {
        MessagingProviderRegistry registry = mock(MessagingProviderRegistry.class);
        when(registry.resolve(MessageChannel.EMAIL)).thenReturn(provider("carepilot-email", MessageChannel.EMAIL));
        when(registry.resolve(MessageChannel.SMS)).thenReturn(provider("carepilot-sms-generic-http", MessageChannel.SMS));
        when(registry.resolve(MessageChannel.WHATSAPP)).thenReturn(provider("WHATSAPP_NOT_CONFIGURED", MessageChannel.WHATSAPP));

        CarePilotEmailMessagingProperties email = new CarePilotEmailMessagingProperties();
        CarePilotSmsMessagingProperties sms = new CarePilotSmsMessagingProperties();
        sms.setEnabled(true);
        sms.setProvider("generic-http");
        sms.setApiUrl("https://sms.example/send");
        sms.setApiKey("secret");
        sms.setFromNumber("CLINIC");
        CarePilotWhatsAppMessagingProperties whatsApp = new CarePilotWhatsAppMessagingProperties();

        CarePilotMessagingStatusService service = new CarePilotMessagingStatusService(
                registry, email, sms, whatsApp, "logging", false, ""
        );

        var smsStatus = service.providerStatuses().stream()
                .filter(s -> s.channel() == MessageChannel.SMS)
                .findFirst()
                .orElseThrow();

        assertThat(smsStatus.status()).isEqualTo(ProviderReadinessStatus.READY);
        assertThat(smsStatus.message()).contains("ready");
    }

    @Test
    void whatsAppConfiguredMetaCloudApiReturnsReady() {
        MessagingProviderRegistry registry = mock(MessagingProviderRegistry.class);
        when(registry.resolve(MessageChannel.EMAIL)).thenReturn(provider("carepilot-email", MessageChannel.EMAIL));
        when(registry.resolve(MessageChannel.SMS)).thenReturn(provider("carepilot-sms-generic-http", MessageChannel.SMS));
        when(registry.resolve(MessageChannel.WHATSAPP)).thenReturn(provider("carepilot-whatsapp-meta-cloud-api", MessageChannel.WHATSAPP));

        CarePilotEmailMessagingProperties email = new CarePilotEmailMessagingProperties();
        CarePilotSmsMessagingProperties sms = new CarePilotSmsMessagingProperties();
        CarePilotWhatsAppMessagingProperties whatsApp = new CarePilotWhatsAppMessagingProperties();
        whatsApp.setEnabled(true);
        whatsApp.setProvider("meta-cloud-api");
        whatsApp.setApiUrl("https://graph.facebook.com/v18.0/123/messages");
        whatsApp.setAccessToken("secret");
        whatsApp.setPhoneNumberId("123");
        whatsApp.setBusinessAccountId("biz-1");

        CarePilotMessagingStatusService service = new CarePilotMessagingStatusService(
                registry, email, sms, whatsApp, "logging", false, ""
        );

        var whatsAppStatus = service.providerStatuses().stream()
                .filter(s -> s.channel() == MessageChannel.WHATSAPP)
                .findFirst()
                .orElseThrow();

        assertThat(whatsAppStatus.status()).isEqualTo(ProviderReadinessStatus.READY);
        assertThat(whatsAppStatus.message()).contains("ready");
    }

    private MessageProvider provider(String providerName, MessageChannel channel) {
        return new MessageProvider() {
            @Override
            public boolean supports(MessageChannel messageChannel) {
                return messageChannel == channel;
            }

            @Override
            public MessageResult send(MessageRequest request) {
                return MessageResult.notConfigured(providerName, "not configured");
            }

            @Override
            public String providerName() {
                return providerName;
            }
        };
    }
}
