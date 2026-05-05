package com.deepthoughtnet.clinic.platform.core.security;

import java.util.UUID;

public interface AppUserProvisioner {
    UUID upsertAndReturnId(UUID tenantId, String keycloakSub, String email, String displayName);
}
