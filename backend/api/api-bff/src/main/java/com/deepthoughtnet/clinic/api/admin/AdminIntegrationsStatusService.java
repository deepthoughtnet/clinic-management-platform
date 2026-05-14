package com.deepthoughtnet.clinic.api.admin;

import com.deepthoughtnet.clinic.api.admin.dto.AdminIntegrationsDtos.IntegrationStatus;
import com.deepthoughtnet.clinic.api.admin.dto.AdminIntegrationsDtos.IntegrationStatusRow;
import com.deepthoughtnet.clinic.api.ai.service.AiStatusService;
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

    public AdminIntegrationsStatusService(
            CarePilotMessagingStatusService messagingStatusService,
            CarePilotWhatsAppMessagingProperties whatsAppProperties,
            CarePilotSmsMessagingProperties smsProperties,
            AiStatusService aiStatusService
    ) {
        this.messagingStatusService = messagingStatusService;
        this.whatsAppProperties = whatsAppProperties;
        this.smsProperties = smsProperties;
        this.aiStatusService = aiStatusService;
    }

    /**
     * Returns grouped integration rows for messaging/webhooks/webinar/AI-voice.
     */
    public List<IntegrationStatusRow> status(UUID tenantId) {
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
                p.missingConfigurationKeys(),
                safeHintsForMessaging(p.channel().name()),
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
                List.of("carepilot.messaging.whatsapp.webhook-verify-token"),
                List.of(
                        "CLINIC_CAREPILOT_WHATSAPP_WEBHOOK_VERIFY_TOKEN=<token>",
                        "CLINIC_CAREPILOT_WHATSAPP_APP_SECRET=<secret>"
                ),
                now
        ));

        rows.add(webhookRow(
                "webhook.sms",
                "SMS Webhook",
                smsProperties.isEnabled(),
                StringUtils.hasText(smsProperties.getWebhookSecret()),
                smsProperties.getProvider(),
                List.of("carepilot.messaging.sms.webhook-secret"),
                List.of("CLINIC_CAREPILOT_SMS_WEBHOOK_SECRET=<secret>"),
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
                List.of("Store secure webinar links (HTTPS) in webinar records"),
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
                ai.providerConfigured() ? List.of() : List.of("clinic.ai.provider", "clinic.ai.enabled"),
                List.of(
                        "CLINIC_AI_ENABLED=true",
                        "CLINIC_AI_PROVIDER=gemini",
                        "CLINIC_AI_GEMINI_API_KEY=<secret>"
                ),
                ai.message(),
                now,
                false
        ));
        rows.add(futureRow("voice.provider", "Voice Calling", "AI_VOICE", now));
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
            message = name + " is disabled because channel is disabled.";
        } else if (!configured) {
            status = IntegrationStatus.NOT_CONFIGURED;
            message = name + " is enabled but secret/token configuration is missing.";
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
                List.of(),
                "Planned for future release.",
                now,
                false
        );
    }

    private List<String> safeHintsForMessaging(String channel) {
        if ("EMAIL".equals(channel)) {
            return List.of(
                    "CLINIC_CAREPILOT_MESSAGING_EMAIL_ENABLED=true",
                    "CLINIC_CAREPILOT_MESSAGING_EMAIL_FROM_ADDRESS=carepilot@clinic.com",
                    "SPRING_MAIL_HOST=<smtp-host>",
                    "SPRING_MAIL_PORT=<smtp-port>"
            );
        }
        if ("SMS".equals(channel)) {
            return List.of(
                    "CLINIC_CAREPILOT_MESSAGING_SMS_ENABLED=true",
                    "CLINIC_CAREPILOT_MESSAGING_SMS_PROVIDER=generic-http",
                    "CLINIC_CAREPILOT_MESSAGING_SMS_API_URL=https://provider/send"
            );
        }
        return List.of(
                "CLINIC_CAREPILOT_MESSAGING_WHATSAPP_ENABLED=true",
                "CLINIC_CAREPILOT_MESSAGING_WHATSAPP_PROVIDER=meta-cloud-api",
                "CLINIC_CAREPILOT_MESSAGING_WHATSAPP_PHONE_NUMBER_ID=<id>",
                "CLINIC_CAREPILOT_MESSAGING_WHATSAPP_BUSINESS_ACCOUNT_ID=<id>"
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
