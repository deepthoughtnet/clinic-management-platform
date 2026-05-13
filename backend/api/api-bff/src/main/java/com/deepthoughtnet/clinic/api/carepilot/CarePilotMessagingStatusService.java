package com.deepthoughtnet.clinic.api.carepilot;

import com.deepthoughtnet.clinic.api.carepilot.dto.MessagingDtos.ProviderReadinessStatus;
import com.deepthoughtnet.clinic.api.carepilot.dto.MessagingDtos.ProviderStatusResponse;
import com.deepthoughtnet.clinic.carepilot.messaging.resolver.MessagingProviderRegistry;
import com.deepthoughtnet.clinic.messaging.email.CarePilotEmailMessagingProperties;
import com.deepthoughtnet.clinic.messaging.sms.CarePilotSmsMessagingProperties;
import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.deepthoughtnet.clinic.messaging.spi.MessageProvider;
import com.deepthoughtnet.clinic.messaging.whatsapp.CarePilotWhatsAppMessagingProperties;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Computes tenant-safe readiness summaries for CarePilot channel providers.
 */
@Service
public class CarePilotMessagingStatusService {
    private final MessagingProviderRegistry providerRegistry;
    private final CarePilotEmailMessagingProperties emailProperties;
    private final CarePilotSmsMessagingProperties smsProperties;
    private final CarePilotWhatsAppMessagingProperties whatsAppProperties;
    private final String mailProvider;
    private final boolean mailEnabled;
    private final String smtpHost;

    public CarePilotMessagingStatusService(
            MessagingProviderRegistry providerRegistry,
            CarePilotEmailMessagingProperties emailProperties,
            CarePilotSmsMessagingProperties smsProperties,
            CarePilotWhatsAppMessagingProperties whatsAppProperties,
            @Value("${clinic.mail.provider:logging}") String mailProvider,
            @Value("${clinic.mail.enabled:false}") boolean mailEnabled,
            @Value("${clinic.mail.host:${spring.mail.host:}}") String smtpHost
    ) {
        this.providerRegistry = providerRegistry;
        this.emailProperties = emailProperties;
        this.smsProperties = smsProperties;
        this.whatsAppProperties = whatsAppProperties;
        this.mailProvider = mailProvider;
        this.mailEnabled = mailEnabled;
        this.smtpHost = smtpHost;
    }

    /**
     * Returns channel readiness rows for EMAIL, SMS, and WHATSAPP.
     */
    public List<ProviderStatusResponse> providerStatuses() {
        OffsetDateTime checkedAt = OffsetDateTime.now();
        return List.of(
                emailStatus(checkedAt),
                smsStatus(checkedAt),
                whatsAppStatus(checkedAt)
        );
    }

    private ProviderStatusResponse emailStatus(OffsetDateTime checkedAt) {
        MessageProvider provider = providerRegistry.resolve(MessageChannel.EMAIL);
        boolean available = isConcreteProvider(provider);
        boolean enabled = emailProperties.isEnabled();
        boolean smtpHostConfigured = StringUtils.hasText(smtpHost);
        boolean providerConfigured = mailEnabled && "smtp".equalsIgnoreCase(mailProvider);
        boolean fromAddressConfigured = StringUtils.hasText(emailProperties.getFromAddress());
        boolean configured = providerConfigured && fromAddressConfigured && smtpHostConfigured;

        List<String> missing = new ArrayList<>();
        if (!providerConfigured) {
            missing.add("clinic.mail.provider=smtp + clinic.mail.enabled=true");
        }
        if (!smtpHostConfigured) {
            missing.add("clinic.mail.host or spring.mail.host");
        }
        if (!fromAddressConfigured) {
            missing.add("carepilot.messaging.email.from-address");
        }

        ProviderReadinessStatus status;
        String message;
        if (!enabled) {
            status = ProviderReadinessStatus.DISABLED;
            message = "Email provider is disabled.";
        } else if (!available) {
            status = ProviderReadinessStatus.ERROR;
            message = "No concrete email provider is available in registry.";
        } else if (!configured) {
            status = ProviderReadinessStatus.NOT_CONFIGURED;
            message = "Email is enabled but SMTP host/from address is not configured.";
        } else {
            status = ProviderReadinessStatus.READY;
            message = "Email provider is configured and ready.";
        }

        return new ProviderStatusResponse(
                MessageChannel.EMAIL,
                provider.providerName(),
                enabled,
                configured,
                available,
                status,
                List.copyOf(missing),
                message,
                true,
                checkedAt,
                providerConfigured,
                fromAddressConfigured,
                false,
                smtpHostConfigured
        );
    }

    private ProviderStatusResponse smsStatus(OffsetDateTime checkedAt) {
        MessageProvider provider = providerRegistry.resolve(MessageChannel.SMS);
        boolean available = isConcreteProvider(provider);
        boolean enabled = smsProperties.isEnabled();
        String smsProvider = smsProperties.getProvider() == null ? "" : smsProperties.getProvider().trim();
        boolean providerConfigured = StringUtils.hasText(smsProvider)
                && !"disabled".equalsIgnoreCase(smsProvider);
        boolean fromNumberConfigured = StringUtils.hasText(smsProperties.getFromNumber())
                || StringUtils.hasText(smsProperties.getSenderId());
        boolean apiUrlConfigured = StringUtils.hasText(smsProperties.getApiUrl());
        boolean apiKeyConfigured = StringUtils.hasText(smsProperties.getApiKey());
        boolean configured = providerConfigured && fromNumberConfigured
                && (!"generic-http".equalsIgnoreCase(smsProvider)
                || (apiUrlConfigured && apiKeyConfigured));

        List<String> missing = new ArrayList<>();
        if (!providerConfigured) {
            missing.add("carepilot.messaging.sms.provider");
        }
        if (!fromNumberConfigured) {
            missing.add("carepilot.messaging.sms.from-number or carepilot.messaging.sms.sender-id");
        }
        if ("generic-http".equalsIgnoreCase(smsProvider)) {
            if (!apiUrlConfigured) {
                missing.add("carepilot.messaging.sms.api-url");
            }
            if (!apiKeyConfigured) {
                missing.add("carepilot.messaging.sms.api-key");
            }
        }

        ProviderReadinessStatus status;
        String message;
        if (!enabled) {
            status = ProviderReadinessStatus.DISABLED;
            message = "SMS channel is disabled.";
        } else if (!available) {
            status = ProviderReadinessStatus.ERROR;
            message = "No concrete SMS provider is available in registry.";
        } else if (!configured) {
            status = ProviderReadinessStatus.NOT_CONFIGURED;
            message = "SMS adapter foundation is available, but provider configuration is incomplete.";
        } else {
            status = ProviderReadinessStatus.READY;
            message = "SMS provider is configured and ready.";
        }

        return new ProviderStatusResponse(
                MessageChannel.SMS,
                provider.providerName(),
                enabled,
                configured,
                available,
                status,
                List.copyOf(missing),
                message,
                true,
                checkedAt,
                providerConfigured,
                false,
                fromNumberConfigured,
                false
        );
    }

    private ProviderStatusResponse whatsAppStatus(OffsetDateTime checkedAt) {
        MessageProvider provider = providerRegistry.resolve(MessageChannel.WHATSAPP);
        boolean available = isConcreteProvider(provider);
        boolean enabled = whatsAppProperties.isEnabled();
        String waProvider = whatsAppProperties.getProvider() == null ? "" : whatsAppProperties.getProvider().trim();
        boolean providerConfigured = "meta-cloud-api".equalsIgnoreCase(waProvider);
        boolean apiUrlConfigured = StringUtils.hasText(whatsAppProperties.getApiUrl());
        boolean accessTokenConfigured = StringUtils.hasText(whatsAppProperties.getAccessToken());
        boolean phoneNumberIdConfigured = StringUtils.hasText(whatsAppProperties.getPhoneNumberId());
        boolean fromNumberConfigured = StringUtils.hasText(whatsAppProperties.getFromNumber());
        boolean businessIdConfigured = StringUtils.hasText(whatsAppProperties.getBusinessAccountId());
        boolean configured = providerConfigured && apiUrlConfigured && accessTokenConfigured && phoneNumberIdConfigured;

        List<String> missing = new ArrayList<>();
        if (!providerConfigured) {
            missing.add("carepilot.messaging.whatsapp.provider=meta-cloud-api");
        }
        if (!apiUrlConfigured) {
            missing.add("carepilot.messaging.whatsapp.api-url");
        }
        if (!accessTokenConfigured) {
            missing.add("carepilot.messaging.whatsapp.access-token");
        }
        if (!phoneNumberIdConfigured) {
            missing.add("carepilot.messaging.whatsapp.phone-number-id");
        }

        ProviderReadinessStatus status;
        String message;
        if (!enabled) {
            status = ProviderReadinessStatus.DISABLED;
            message = "WhatsApp channel is disabled.";
        } else if (!available) {
            status = ProviderReadinessStatus.ERROR;
            message = "No concrete WhatsApp provider is available in registry.";
        } else if (!configured) {
            status = ProviderReadinessStatus.NOT_CONFIGURED;
            message = "WhatsApp adapter foundation is available, but provider configuration is incomplete.";
        } else {
            status = ProviderReadinessStatus.READY;
            message = "WhatsApp provider is configured and ready.";
        }

        return new ProviderStatusResponse(
                MessageChannel.WHATSAPP,
                provider.providerName(),
                enabled,
                configured,
                available,
                status,
                List.copyOf(missing),
                message,
                true,
                checkedAt,
                providerConfigured,
                false,
                fromNumberConfigured || businessIdConfigured,
                false
        );
    }

    private boolean isConcreteProvider(MessageProvider provider) {
        return provider != null && !"carepilot-noop".equals(provider.providerName());
    }
}
