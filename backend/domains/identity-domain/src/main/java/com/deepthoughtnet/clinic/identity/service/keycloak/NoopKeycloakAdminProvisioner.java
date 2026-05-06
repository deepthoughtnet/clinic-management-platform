package com.deepthoughtnet.clinic.identity.service.keycloak;

import java.util.UUID;

/**
 * Local/dev fallback when Keycloak Admin API integration is disabled.
 */
public class NoopKeycloakAdminProvisioner implements KeycloakAdminProvisioner {

    private static final String DISABLED_MESSAGE =
            "Keycloak Admin provisioning is disabled (clinic.keycloak.admin.enabled=false)";

    @Override
    public String findUserIdByEmailOrUsername(String email, String username) {
        return null;
    }

    @Override
    public String createOrGetTenantAdminUserId(UUID tenantId, String email, String displayName, String tempPassword) {
        throw new IllegalStateException(DISABLED_MESSAGE);
    }

    @Override
    public String createOrGetTenantUserId(
            UUID tenantId,
            String email,
            String username,
            String displayName,
            String tempPassword,
            boolean emailVerified
    ) {
        throw new IllegalStateException(DISABLED_MESSAGE);
    }

    @Override
    public void ensureRealmRole(String userId, String roleName) {
        throw new IllegalStateException(DISABLED_MESSAGE);
    }

    @Override
    public void deleteUser(String userId) {
        // best-effort no-op in disabled mode
    }
}
