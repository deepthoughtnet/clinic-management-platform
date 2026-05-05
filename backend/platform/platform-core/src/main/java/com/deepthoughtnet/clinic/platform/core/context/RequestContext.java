package com.deepthoughtnet.clinic.platform.core.context;

import java.util.Set;
import java.util.UUID;

public record RequestContext(
        TenantId tenantId,
        UUID appUserId,
        String keycloakSub,
        Set<String> tokenRoles,      // roles from JWT (realm/client) - informational
        String tenantRole,           // role from tenant_memberships (authoritative)
        String correlationId
) {}
