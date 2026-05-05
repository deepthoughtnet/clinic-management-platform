package com.deepthoughtnet.clinic.platform.core.security;

import java.util.Set;
import java.util.UUID;

/**
 * Pure (non-Spring) contract for resolving a user's authoritative role within a tenant.
 */
public interface TenantRoleResolver {
    String resolveTenantRole(UUID tenantId, UUID appUserId, Set<String> tokenRolesUpper);
}
