package com.deepthoughtnet.clinic.discover.verification;

import java.time.OffsetDateTime;
import java.util.UUID;

public record VerificationChallengeResult(
        UUID challengeId,
        VerificationChannel channel,
        String maskedRecipient,
        String message,
        String developmentCode,
        String verificationMode,
        OffsetDateTime expiresAt,
        OffsetDateTime resendAvailableAt,
        long expiresInSeconds,
        long resendAfterSeconds,
        String providerName,
        String deliveryReference
) {
}
