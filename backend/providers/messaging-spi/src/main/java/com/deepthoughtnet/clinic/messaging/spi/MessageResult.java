package com.deepthoughtnet.clinic.messaging.spi;

import java.time.OffsetDateTime;

/**
 * Result emitted by a message provider after dispatch attempt.
 */
public record MessageResult(
        boolean success,
        MessageDeliveryStatus status,
        String providerName,
        String providerMessageId,
        String errorCode,
        String errorMessage,
        OffsetDateTime sentAt
) {
    public MessageResult {
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        if (providerName == null || providerName.isBlank()) {
            throw new IllegalArgumentException("providerName is required");
        }
    }

    public static MessageResult notConfigured(String providerName, String message) {
        return new MessageResult(false, MessageDeliveryStatus.NOT_CONFIGURED, providerName, null, "NOT_CONFIGURED", message, null);
    }

    public static MessageResult providerUnavailable(String providerName, String message) {
        return new MessageResult(false, MessageDeliveryStatus.PROVIDER_NOT_AVAILABLE, providerName, null, "PROVIDER_NOT_AVAILABLE", message, null);
    }
}
