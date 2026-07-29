package com.deepthoughtnet.clinic.discover.verification;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProviderSessionResult(
        UUID providerAccountId,
        String sessionToken,
        OffsetDateTime expiresAt
) {
}
