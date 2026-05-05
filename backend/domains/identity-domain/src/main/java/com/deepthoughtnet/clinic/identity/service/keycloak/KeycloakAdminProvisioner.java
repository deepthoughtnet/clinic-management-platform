package com.deepthoughtnet.clinic.identity.service.keycloak;

import java.util.UUID;

public interface KeycloakAdminProvisioner {

    /**
     * Best-effort search to find an existing userId by email and/or username.
     * Returns null if not found.
     */
    String findUserIdByEmailOrUsername(String email, String username);

    /**
     * Creates or finds a Keycloak user, ensures tenant_id attribute exists,
     * and returns the Keycloak userId (which is also the JWT "sub").
     */
    String createOrGetTenantAdminUserId(UUID tenantId, String email, String displayName, String tempPassword);

    /**
     * Generic tenant-scoped user creation/upsert used by tenant admin flows
     * (Parents/Drivers/etc). Ensures:
     * - user exists (create if missing)
     * - tenant_id attribute set
     * - enabled=true
     * - email + username set (when provided)
     * - optional emailVerified
     * - optional temp password reset (temporary=true)
     *
     * Returns Keycloak userId (JWT sub).
     */
    String createOrGetTenantUserId(
            UUID tenantId,
            String email,
            String username,
            String displayName,
            String tempPassword,
            boolean emailVerified
    );

    /**
     * Ensures the realm role is assigned to the user.
     */
    void ensureRealmRole(String userId, String roleName);

    /**
     * Best-effort delete (used for compensation if DB write fails after KC user created).
     */
    void deleteUser(String userId);
}