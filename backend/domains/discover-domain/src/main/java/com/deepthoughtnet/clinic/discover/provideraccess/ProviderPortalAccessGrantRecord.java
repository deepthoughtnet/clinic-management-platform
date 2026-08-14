package com.deepthoughtnet.clinic.discover.provideraccess;

import java.util.UUID;

public record ProviderPortalAccessGrantRecord(
        UUID providerAccountId,
        String providerAccountDisplayName,
        String providerApplicationReference
) {
}
