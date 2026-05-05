package com.deepthoughtnet.clinic.platform.core.security;

import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import java.util.Set;

public interface AuthContextExtractor {
    String keycloakSub();
    String email();
    String displayName();
    Set<String> rolesUpper();
    TenantId resolveTenantId(String tenantHeader);
}
