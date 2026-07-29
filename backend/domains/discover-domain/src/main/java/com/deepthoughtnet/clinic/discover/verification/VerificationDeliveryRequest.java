package com.deepthoughtnet.clinic.discover.verification;

import java.util.Map;
import java.util.UUID;

public record VerificationDeliveryRequest(
        UUID providerApplicationId,
        UUID providerAccountId,
        VerificationPurpose purpose,
        VerificationChannel channel,
        String normalizedRecipient,
        String code,
        String subject,
        String body,
        Map<String, String> metadata
) {
    public VerificationDeliveryRequest {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        if (purpose == null) {
            throw new IllegalArgumentException("purpose is required");
        }
        if (channel == null) {
            throw new IllegalArgumentException("channel is required");
        }
        if (normalizedRecipient == null || normalizedRecipient.isBlank()) {
            throw new IllegalArgumentException("normalizedRecipient is required");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
    }
}
