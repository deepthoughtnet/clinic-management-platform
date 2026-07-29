package com.deepthoughtnet.clinic.api.discover.provider.auth;

import java.util.Set;
import java.util.UUID;

public record ProviderSessionPrincipal(
        UUID providerAccountId,
        UUID sessionId,
        Set<String> roles
) {
}
