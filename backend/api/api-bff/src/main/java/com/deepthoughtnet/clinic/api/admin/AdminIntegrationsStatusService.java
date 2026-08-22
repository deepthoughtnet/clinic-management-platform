package com.deepthoughtnet.clinic.api.admin;

import com.deepthoughtnet.clinic.api.admin.dto.AdminIntegrationsDtos.IntegrationStatus;
import com.deepthoughtnet.clinic.api.admin.dto.AdminIntegrationsDtos.IntegrationStatusRow;
import com.deepthoughtnet.clinic.api.ai.service.AiStatusService;
import com.deepthoughtnet.clinic.carepilot.ai_call.provider.VoiceCallProviderRegistry;
import com.deepthoughtnet.clinic.api.carepilot.CarePilotMessagingStatusService;
import com.deepthoughtnet.clinic.api.carepilot.dto.MessagingDtos.ProviderReadinessStatus;
import com.deepthoughtnet.clinic.messaging.sms.CarePilotSmsMessagingProperties;
import com.deepthoughtnet.clinic.messaging.whatsapp.CarePilotWhatsAppMessagingProperties;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Composes tenant-safe integrations readiness rows for Administration UI.
 */
@Service
public class AdminIntegrationsStatusService {
    private final CarePilotMessagingStatusService messagingStatusService;
    private final CarePilotWhatsAppMessagingProperties whatsAppProperties;
    private final CarePilotSmsMessagingProperties smsProperties;
    private final AiStatusService aiStatusService;
    private final VoiceCallProviderRegistry voiceProviderRegistry;

    public AdminIntegrationsStatusService(
            CarePilotMessagingStatusService messagingStatusService,
            CarePilotWhatsAppMessagingProperties whatsAppProperties,
            CarePilotSmsMessagingProperties smsProperties,
            AiStatusService aiStatusService,
            VoiceCallProviderRegistry voiceProviderRegistry
    ) {
        this.messagingStatusService = messagingStatusService;
        this.whatsAppProperties = whatsAppProperties;
        this.smsProperties = smsProperties;
        this.aiStatusService = aiStatusService;
        this.voiceProviderRegistry = voiceProviderRegistry;
    }

    public List<IntegrationStatusRow> status(UUID tenantId) {
        return status(tenantId, false);
    }

    /**
     * Returns grouped integration rows for messaging/webhooks/webinar/AI-voice.
     */
    public List<IntegrationStatusRow> status(UUID tenantId, boolean includeTechnicalDetails) {
        List<IntegrationStatusRow> rows = new ArrayList<>();
        var providers = messagingStatusService.providerStatuses();
        OffsetDateTime now = OffsetDateTime.now();

        providers.forEach(p -> rows.add(new IntegrationStatusRow(
                "messaging." + p.channel().name().toLowerCase(),
                switch (p.channel()) {
                    case EMAIL -> "Email / SMTP";
                    case SMS -> "SMS";
                    case WHATSAPP -> "WhatsApp";
                    case IN_APP -> "In-App";
                },
                "MESSAGING",
                mapStatus(p.status()),
                p.enabled(),
                p.configured(),
                p.providerName(),
                includeTechnicalDetails ? p.missingConfigurationKeys() : List.of(),
                safeHintsForMessaging(p.channel().name(), p.status(), p.enabled(), p.configured()),
                p.message(),
                p.lastCheckedAt(),
                p.supportsTestSend()
        )));

        rows.add(webhookRow(
                "webhook.whatsapp",
                "WhatsApp Webhook",
                whatsAppProperties.isEnabled(),
                StringUtils.hasText(whatsAppProperties.getWebhookVerifyToken()),
                "carepilot-whatsapp-meta-cloud-api",
                includeTechnicalDetails ? List.of("carepilot.messaging.whatsapp.webhook-verify-token") : List.of(),
                webhookGuidance("WhatsApp", whatsAppProperties.isEnabled(), StringUtils.hasText(whatsAppProperties.getWebhookVerifyToken())),
                now
        ));

        rows.add(webhookRow(
                "webhook.sms",
                "SMS Webhook",
                smsProperties.isEnabled(),
                StringUtils.hasText(smsProperties.getWebhookSecret()),
                smsProperties.getProvider(),
                includeTechnicalDetails ? List.of("carepilot.messaging.sms.webhook-secret") : List.of(),
                webhookGuidance("SMS", smsProperties.isEnabled(), StringUtils.hasText(smsProperties.getWebhookSecret())),
                now
        ));

        rows.add(new IntegrationStatusRow(
                "webinar.external-url",
                "External Webinar URL",
                "WEBINAR",
                IntegrationStatus.READY,
                true,
                true,
                "external-url",
                List.of(),
                List.of("External HTTPS webinar links are supported."),
                "External webinar links are supported.",
                now,
                false
        ));
        rows.add(futureRow("webinar.zoom", "Zoom", "WEBINAR", now));
        rows.add(futureRow("webinar.google-meet", "Google Meet", "WEBINAR", now));
        rows.add(futureRow("webinar.teams", "Microsoft Teams", "WEBINAR", now));

        var ai = aiStatusService.status(tenantId);
        rows.add(new IntegrationStatusRow(
                "ai.orchestration",
                "AI Orchestration",
                "AI_VOICE",
                ai.providerConfigured() && ai.runtimeEnabled() ? IntegrationStatus.READY : IntegrationStatus.NOT_CONFIGURED,
                ai.runtimeEnabled(),
                ai.providerConfigured(),
                ai.provider(),
                includeTechnicalDetails && !ai.providerConfigured() ? List.of("clinic.ai.provider", "clinic.ai.enabled") : List.of(),
                ai.providerConfigured()
                        ? List.of("AI provider is configured and ready.", "Provider credentials are managed securely by Platform Admin.")
                        : List.of("AI provider is not configured.", "Platform Admin must choose a provider and provide credentials."),
                ai.message(),
                now,
                false
        ));
        var voiceProvider = voiceProviderRegistry.resolve();
        rows.add(new IntegrationStatusRow(
                "voice.provider",
                "Voice Calling",
                "AI_VOICE",
                voiceProvider.isReady() ? IntegrationStatus.READY : IntegrationStatus.NOT_CONFIGURED,
                voiceProvider.isReady(),
                voiceProvider.isReady(),
                voiceProvider.providerName(),
                includeTechnicalDetails && !voiceProvider.isReady() ? List.of("carepilot.voice.mock.enabled") : List.of(),
                voiceProvider.isReady()
                        ? List.of("Voice provider is configured.", "Platform Admin can manage production provider settings.")
                        : List.of("Voice provider is not configured.", "Platform Admin can enable a supported voice provider or keep the mock provider active."),
                voiceProvider.isReady() ? "Voice provider is ready." : "Voice provider is not configured yet.",
                now,
                false
        ));
        rows.add(futureRow("voice.stt-tts", "STT/TTS", "AI_VOICE", now));

        return List.copyOf(rows);
    }

    private IntegrationStatusRow webhookRow(
            String key,
            String name,
            boolean enabled,
            boolean configured,
            String provider,
            List<String> missingKeys,
            List<String> hints,
            OffsetDateTime now
    ) {
        IntegrationStatus status;
        String message;
        if (!enabled) {
            status = IntegrationStatus.DISABLED;
            message = name + " is unavailable because the parent channel is disabled.";
        } else if (!configured) {
            status = IntegrationStatus.NOT_CONFIGURED;
            message = name + " requires platform setup before it can be used.";
        } else {
            status = IntegrationStatus.READY;
            message = name + " is configured.";
        }
        return new IntegrationStatusRow(
                key,
                name,
                "WEBHOOK",
                status,
                enabled,
                configured,
                provider,
                configured ? List.of() : missingKeys,
                hints,
                message,
                now,
                false
        );
    }

    private IntegrationStatusRow futureRow(String key, String name, String category, OffsetDateTime now) {
        return new IntegrationStatusRow(
                key,
                name,
                category,
                IntegrationStatus.FUTURE,
                false,
                false,
                null,
                List.of(),
                List.of("Planned for a future release."),
                "Planned for future release.",
                now,
                false
        );
    }

    private List<String> safeHintsForMessaging(String channel, ProviderReadinessStatus status, boolean enabled, boolean configured) {
        if ("EMAIL".equals(channel)) {
            if (status == ProviderReadinessStatus.READY) {
                return List.of(
                        "SMTP server configured.",
                        "Sender address configured.",
                        "Email delivery is ready.",
                        "Contact Platform Admin if configuration changes are required."
                );
            }
            return List.of(
                    "SMTP server configuration is incomplete.",
                    "Configure the SMTP server and sender address.",
                    "Contact Platform Admin to complete email setup."
            );
        }
        if ("SMS".equals(channel)) {
            if (status == ProviderReadinessStatus.READY) {
                return List.of(
                        "SMS provider is configured and ready.",
                        "SMS delivery is enabled for the tenant.",
                        "Contact Platform Admin for provider changes."
                );
            }
            if (!enabled || status == ProviderReadinessStatus.DISABLED) {
                return List.of(
                        "SMS provider is disabled or not configured.",
                        "Contact Platform Admin to enable SMS delivery."
                );
            }
            return List.of(
                    "SMS provider is not configured.",
                    "Configure an SMS provider and sender identity.",
                    "Contact Platform Admin to enable SMS delivery."
            );
        }
        if (status == ProviderReadinessStatus.READY) {
            return List.of(
                    "WhatsApp provider is configured and ready.",
                    "WhatsApp delivery is enabled for the tenant.",
                    "Contact Platform Admin for provider changes."
            );
        }
        if (!enabled || status == ProviderReadinessStatus.DISABLED) {
            return List.of(
                    "WhatsApp provider is disabled or not configured.",
                    "Contact Platform Admin to complete credentials and verification."
            );
        }
        return List.of(
                "WhatsApp provider is not configured.",
                "Connect a supported WhatsApp Business provider and phone number.",
                "Contact Platform Admin to complete credentials and verification."
        );
    }

    private List<String> webhookGuidance(String channel, boolean enabled, boolean configured) {
        if (!enabled) {
            return List.of(
                    channel + " webhook is unavailable because " + channel.toLowerCase() + " is disabled.",
                    "Webhook verification must be configured by Platform Admin before use."
            );
        }
        if (!configured) {
            return List.of(
                    channel + " webhook is not configured.",
                    "Webhook verification must be configured by Platform Admin before use."
            );
        }
        return List.of(
                channel + " webhook is configured and ready.",
                "Webhook verification is managed by Platform Admin."
        );
    }

    private IntegrationStatus mapStatus(ProviderReadinessStatus status) {
        return switch (status) {
            case READY -> IntegrationStatus.READY;
            case DISABLED -> IntegrationStatus.DISABLED;
            case NOT_CONFIGURED -> IntegrationStatus.NOT_CONFIGURED;
            case ERROR -> IntegrationStatus.ERROR;
        };
    }
}
