package com.deepthoughtnet.clinic.api.carepilot.dto;

import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * DTOs for CarePilot messaging provider visibility and diagnostics APIs.
 */
public final class MessagingDtos {
    private MessagingDtos() {
    }

    /**
     * Readiness classification used by the provider-status cards.
     */
    public enum ProviderReadinessStatus {
        READY,
        DISABLED,
        NOT_CONFIGURED,
        ERROR
    }

    /**
     * Tenant-safe view of one channel provider's readiness.
     */
    public record ProviderStatusResponse(
            MessageChannel channel,
            String providerName,
            boolean enabled,
            boolean configured,
            boolean available,
            ProviderReadinessStatus status,
            List<String> missingConfigurationKeys,
            String message,
            boolean supportsTestSend,
            OffsetDateTime lastCheckedAt,
            boolean providerConfigured,
            boolean fromAddressConfigured,
            boolean fromNumberConfigured,
            boolean smtpHostConfigured
    ) {
    }

    /**
     * Request for provider-level test-send diagnostics.
     */
    public record ProviderTestSendRequest(
            @NotBlank String recipient,
            String subject,
            @NotBlank String body
    ) {
    }

    /**
     * Response shape for provider-level test-send diagnostics.
     */
    public record ProviderTestSendResponse(
            MessageChannel channel,
            boolean success,
            MessageDeliveryStatus status,
            String providerName,
            String providerMessageId,
            String errorCode,
            String errorMessage,
            OffsetDateTime sentAt
    ) {
    }
}
