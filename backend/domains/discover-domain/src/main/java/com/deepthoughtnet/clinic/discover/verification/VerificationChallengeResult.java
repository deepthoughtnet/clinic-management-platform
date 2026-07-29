package com.deepthoughtnet.clinic.discover.verification;

public record VerificationChallengeResult(
        String message,
        String developmentCode,
        long expiresInSeconds,
        long resendAfterSeconds,
        String providerName,
        String deliveryReference
) {
}
