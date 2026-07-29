package com.deepthoughtnet.clinic.discover.verification;

import java.util.UUID;

public record VerificationVerificationResult(
        boolean verified,
        String message,
        UUID providerAccountId,
        boolean accountCreated,
        boolean accountLinked,
        String normalizedRecipient,
        VerificationPurpose purpose,
        VerificationChannel channel
) {
}
