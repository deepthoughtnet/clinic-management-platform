package com.deepthoughtnet.clinic.discover.verification;

import java.util.UUID;

public record VerificationChallengeRequest(
        UUID providerApplicationId,
        UUID providerAccountId,
        VerificationPurpose purpose,
        VerificationChannel channel,
        String normalizedRecipient,
        String subject,
        String body,
        String createdByContext
) {
}
