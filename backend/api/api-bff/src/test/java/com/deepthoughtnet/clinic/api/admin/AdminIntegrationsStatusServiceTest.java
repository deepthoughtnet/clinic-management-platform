package com.deepthoughtnet.clinic.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.ai.dto.AiStatusResponse;
import com.deepthoughtnet.clinic.api.ai.service.AiStatusService;
import com.deepthoughtnet.clinic.api.carepilot.CarePilotMessagingStatusService;
import com.deepthoughtnet.clinic.api.carepilot.dto.MessagingDtos.ProviderReadinessStatus;
import com.deepthoughtnet.clinic.api.carepilot.dto.MessagingDtos.ProviderStatusResponse;
import com.deepthoughtnet.clinic.carepilot.ai_call.provider.VoiceCallProviderRegistry;
import com.deepthoughtnet.clinic.messaging.sms.CarePilotSmsMessagingProperties;
import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.deepthoughtnet.clinic.messaging.whatsapp.CarePilotWhatsAppMessagingProperties;
import com.deepthoughtnet.clinic.voice.spi.VoiceCallProvider;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminIntegrationsStatusServiceTest {
    private CarePilotMessagingStatusService messagingStatusService;
    private CarePilotWhatsAppMessagingProperties whatsAppProperties;
    private CarePilotSmsMessagingProperties smsProperties;
    private AiStatusService aiStatusService;
    private VoiceCallProviderRegistry voiceCallProviderRegistry;
    private AdminIntegrationsStatusService service;

    @BeforeEach
    void setUp() {
        messagingStatusService = mock(CarePilotMessagingStatusService.class);
        whatsAppProperties = new CarePilotWhatsAppMessagingProperties();
        smsProperties = new CarePilotSmsMessagingProperties();
        aiStatusService = mock(AiStatusService.class);
        voiceCallProviderRegistry = mock(VoiceCallProviderRegistry.class);
        VoiceCallProvider provider = mock(VoiceCallProvider.class);
        when(provider.isReady()).thenReturn(true);
        when(provider.providerName()).thenReturn("mock-voice");
        when(voiceCallProviderRegistry.resolve()).thenReturn(provider);
        service = new AdminIntegrationsStatusService(messagingStatusService, whatsAppProperties, smsProperties, aiStatusService, voiceCallProviderRegistry);

        when(messagingStatusService.providerStatuses()).thenReturn(List.of(
                new ProviderStatusResponse(MessageChannel.EMAIL, "email-provider", true, true, true, ProviderReadinessStatus.READY,
                        List.of(), "ok", true, OffsetDateTime.now(), true, true, false, true),
                new ProviderStatusResponse(MessageChannel.SMS, "sms-provider", true, false, true, ProviderReadinessStatus.NOT_CONFIGURED,
                        List.of("carepilot.messaging.sms.api-url"), "missing", true, OffsetDateTime.now(), true, false, true, false),
                new ProviderStatusResponse(MessageChannel.WHATSAPP, "wa-provider", false, false, false, ProviderReadinessStatus.DISABLED,
                        List.of(), "disabled", true, OffsetDateTime.now(), false, false, false, false)
        ));
        when(aiStatusService.status(org.mockito.ArgumentMatchers.any())).thenReturn(new AiStatusResponse(
                true, true, "gemini", true, true, true, true, "tesseract", true, "READY", "ok"
        ));
    }

    @Test
    void includesMessagingWebhookAndFutureRowsWithoutSecrets() {
        UUID tenantId = UUID.randomUUID();
        var rows = service.status(tenantId);

        assertThat(rows).anyMatch(r -> r.key().equals("messaging.email"));
        assertThat(rows).anyMatch(r -> r.key().equals("messaging.sms"));
        assertThat(rows).anyMatch(r -> r.key().equals("messaging.whatsapp"));
        assertThat(rows).anyMatch(r -> r.key().equals("webhook.whatsapp"));
        assertThat(rows).anyMatch(r -> r.key().equals("webhook.sms"));
        assertThat(rows).anyMatch(r -> r.key().equals("webinar.zoom") && r.status().name().equals("FUTURE"));

        assertThat(rows.stream().flatMap(r -> r.safeConfigurationHints().stream()).toList())
                .noneMatch(v -> v.toLowerCase().contains("access-token=") || v.toLowerCase().contains("api-key="));
    }
}
