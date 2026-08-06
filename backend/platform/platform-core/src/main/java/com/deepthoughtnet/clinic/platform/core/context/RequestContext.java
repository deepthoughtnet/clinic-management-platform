package com.deepthoughtnet.clinic.platform.core.context;

import java.util.Set;
import java.util.UUID;

public record RequestContext(
        TenantId tenantId,
        UUID appUserId,
        String keycloakSub,
        String actorEmail,
        String actorDisplayName,
        Set<String> tokenRoles,      // roles from JWT (realm/client) - informational
        String tenantRole,           // role from tenant_memberships (authoritative)
        String correlationId
) {
    public RequestContext(TenantId tenantId, UUID appUserId, String keycloakSub, Set<String> tokenRoles, String tenantRole, String correlationId) {
        this(tenantId, appUserId, keycloakSub, null, null, tokenRoles, tenantRole, correlationId);
    }
}
