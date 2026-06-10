package com.deepthoughtnet.clinic.identity.service.keycloak;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * Minimal Keycloak Admin API wrapper used for provisioning.
 *
 * Assumptions:
 * - Tenant-scoped users have a tenant_id claim in token.
 * - tenant_id is mapped from a user attribute "tenant_id" via a protocol mapper.
 */
public class KeycloakAdminProvisionerImpl implements KeycloakAdminProvisioner {
    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminProvisionerImpl.class);
    private static final int MAX_ATTEMPTS = 5;
    private static final long INITIAL_BACKOFF_MILLIS = 1_000L;

    private final Keycloak keycloakAdmin;
    private final String realm;

    public KeycloakAdminProvisionerImpl(Keycloak keycloakAdmin, String realm) {
        this.keycloakAdmin = keycloakAdmin;
        this.realm = realm;
    }

    @Override
    public String findUserIdByEmailOrUsername(String email, String username) {
        return executeWithRetry("find user by email or username", () -> {
            RealmResource rr = keycloakAdmin.realm(realm);

            // Prefer exact email match if provided
            if (StringUtils.hasText(email)) {
                List<UserRepresentation> found = rr.users().search(email, true);
                UserRepresentation existing = found == null ? null : found.stream()
                        .filter(u -> email.equalsIgnoreCase(u.getEmail()))
                        .findFirst()
                        .orElse(null);

                if (existing != null && StringUtils.hasText(existing.getId())) {
                    return existing.getId();
                }
            }

            // Fallback to exact username match if provided
            if (StringUtils.hasText(username)) {
                List<UserRepresentation> found = rr.users().search(username, true);
                UserRepresentation existing = found == null ? null : found.stream()
                        .filter(u -> username.equalsIgnoreCase(u.getUsername()))
                        .findFirst()
                        .orElse(null);

                if (existing != null && StringUtils.hasText(existing.getId())) {
                    return existing.getId();
                }
            }

            return null;
        });
    }

    @Override
    public String createOrGetTenantAdminUserId(UUID tenantId, String email, String displayName, String tempPassword) {
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("email is required for tenant admin provisioning");
        }

        return executeWithRetry("create or get tenant admin user", () -> {
            RealmResource rr = keycloakAdmin.realm(realm);

            // Try find by email
            List<UserRepresentation> found = rr.users().search(email, true);
            UserRepresentation existing = found == null ? null : found.stream()
                    .filter(u -> email.equalsIgnoreCase(u.getEmail()))
                    .findFirst()
                    .orElse(null);

            if (existing != null && existing.getId() != null) {
                ensureTenantAttribute(rr, existing.getId(), tenantId);

                // Ensure enabled
                if (existing.isEnabled() == null || !existing.isEnabled()) {
                    existing.setEnabled(true);
                    rr.users().get(existing.getId()).update(existing);
                }

                // Ensure tenant admin role for clinic realm.
                ensureRealmRoleInternal(rr, existing.getId(), "CLINIC_ADMIN");

                // Optional: reset password
                if (StringUtils.hasText(tempPassword)) {
                    resetTemporaryPasswordInternal(rr, existing.getId(), tempPassword);
                }

                return existing.getId();
            }

            // Create new user
            UserRepresentation u = new UserRepresentation();
            u.setEnabled(true);
            u.setEmail(email);
            u.setUsername(email);
            u.setEmailVerified(Boolean.TRUE);
            if (StringUtils.hasText(displayName)) {
                u.setFirstName(displayName); // keep simple
            }

            Response resp = rr.users().create(u);
            if (resp.getStatus() != 201) {
                String msg = resp.getStatusInfo() == null ? "" : resp.getStatusInfo().toString();
                throw new IllegalStateException("Failed to create tenant admin user in Keycloak. status=" + resp.getStatus() + " " + msg);
            }

            String userId = extractCreatedId(resp);
            ensureTenantAttribute(rr, userId, tenantId);
            ensureRealmRoleInternal(rr, userId, "CLINIC_ADMIN");

            if (StringUtils.hasText(tempPassword)) {
                resetTemporaryPasswordInternal(rr, userId, tempPassword);
            }

            return userId;
        });
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
        if (!StringUtils.hasText(email) && !StringUtils.hasText(username)) {
            throw new IllegalArgumentException("email or username is required");
        }

        return executeWithRetry("create or get tenant user", () -> {
            RealmResource rr = keycloakAdmin.realm(realm);

            // Prefer email search if present; otherwise username search
            UserRepresentation existing = null;

            if (StringUtils.hasText(email)) {
                List<UserRepresentation> found = rr.users().search(email, true);
                existing = found == null ? null : found.stream()
                        .filter(u -> email.equalsIgnoreCase(u.getEmail()))
                        .findFirst()
                        .orElse(null);
            }

            if (existing == null && StringUtils.hasText(username)) {
                List<UserRepresentation> found = rr.users().search(username, true);
                existing = found == null ? null : found.stream()
                        .filter(u -> username.equalsIgnoreCase(u.getUsername()))
                        .findFirst()
                        .orElse(null);
            }

            if (existing != null && existing.getId() != null) {
                ensureTenantAttribute(rr, existing.getId(), tenantId);

                // Ensure enabled
                if (existing.isEnabled() == null || !existing.isEnabled()) {
                    existing.setEnabled(true);
                    rr.users().get(existing.getId()).update(existing);
                }

                // Ensure email/username set if missing
                boolean changed = false;
                if (StringUtils.hasText(email) && !email.equalsIgnoreCase(existing.getEmail())) {
                    existing.setEmail(email);
                    changed = true;
                }
                if (StringUtils.hasText(username) && !username.equalsIgnoreCase(existing.getUsername())) {
                    existing.setUsername(username);
                    changed = true;
                }
                if (StringUtils.hasText(displayName) && (existing.getFirstName() == null || existing.getFirstName().isBlank())) {
                    existing.setFirstName(displayName);
                    changed = true;
                }
                if (emailVerified && (existing.isEmailVerified() == null || !existing.isEmailVerified())) {
                    existing.setEmailVerified(Boolean.TRUE);
                    changed = true;
                }

                if (changed) {
                    rr.users().get(existing.getId()).update(existing);
                }

                if (StringUtils.hasText(tempPassword)) {
                    resetTemporaryPasswordInternal(rr, existing.getId(), tempPassword);
                }

                return existing.getId();
            }

            // Create new user
            UserRepresentation u = new UserRepresentation();
            u.setEnabled(true);

            if (StringUtils.hasText(email)) {
                u.setEmail(email);
                // By default, use email as username when username not provided
                if (!StringUtils.hasText(username)) {
                    u.setUsername(email);
                }
                u.setEmailVerified(emailVerified ? Boolean.TRUE : Boolean.FALSE);
            }

            if (StringUtils.hasText(username)) {
                u.setUsername(username);
            }

            if (StringUtils.hasText(displayName)) {
                u.setFirstName(displayName);
            }

            Response resp = rr.users().create(u);
            if (resp.getStatus() != 201) {
                String msg = resp.getStatusInfo() == null ? "" : resp.getStatusInfo().toString();
                throw new IllegalStateException("Failed to create user in Keycloak. status=" + resp.getStatus() + " " + msg);
            }

            String userId = extractCreatedId(resp);
            ensureTenantAttribute(rr, userId, tenantId);

            if (StringUtils.hasText(tempPassword)) {
                resetTemporaryPasswordInternal(rr, userId, tempPassword);
            }

            return userId;
        });
    }

    @Override
    public void ensureRealmRole(String userId, String roleName) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("userId is required");
        }
        if (!StringUtils.hasText(roleName)) {
            throw new IllegalArgumentException("roleName is required");
        }

        executeWithRetry("ensure realm role", () -> {
            RealmResource rr = keycloakAdmin.realm(realm);
            ensureRealmRoleInternal(rr, userId, roleName);
            return null;
        });
    }

    @Override
    public void resetPassword(String userId, String tempPassword, boolean temporary) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("userId is required");
        }
        if (!StringUtils.hasText(tempPassword)) {
            throw new IllegalArgumentException("tempPassword is required");
        }
        executeWithRetry("reset password", () -> {
            RealmResource rr = keycloakAdmin.realm(realm);
            resetPasswordInternal(rr, userId, tempPassword, temporary);
            return null;
        });
    }

    private RoleRepresentation getOrCreateRealmRole(RealmResource rr, String roleName) {
        try {
            RoleResource roleResource = rr.roles().get(roleName);
            return roleResource.toRepresentation();
        } catch (NotFoundException ex) {
            log.info("Creating missing realm role '{}' in targetRealm='{}'", roleName, realm);
            RoleRepresentation role = new RoleRepresentation();
            role.setName(roleName);
            rr.roles().create(role);
            try {
                return rr.roles().get(roleName).toRepresentation();
            } catch (Exception createLookupEx) {
                throw new IllegalStateException(
                        "Keycloak provisioning failed: role '" + roleName + "' missing in realm '" + realm + "'",
                        createLookupEx
                );
            }
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Keycloak provisioning failed: unable to access role '" + roleName + "' in realm '" + realm + "'",
                    ex
            );
        }
    }

    @Override
    public void deleteUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        try {
            executeWithRetry("delete user", () -> {
                RealmResource rr = keycloakAdmin.realm(realm);
                rr.users().delete(userId);
                return null;
            });
        } catch (Exception ignore) {
            // best-effort
        }
    }

    private void ensureTenantAttribute(RealmResource rr, String userId, UUID tenantId) {
        UserRepresentation u = rr.users().get(userId).toRepresentation();
        Map<String, List<String>> attrs = u.getAttributes();
        if (attrs == null) {
            attrs = new HashMap<>();
        }
        String tenant = tenantId.toString();

        List<String> existing = attrs.get("tenant_id");
        if (existing == null || existing.isEmpty() || !tenant.equals(existing.get(0))) {
            attrs.put("tenant_id", List.of(tenant));
            u.setAttributes(attrs);
            rr.users().get(userId).update(u);
        }
    }

    private void ensureRealmRoleInternal(RealmResource rr, String userId, String roleName) {
        RoleRepresentation role = getOrCreateRealmRole(rr, roleName);

        List<RoleRepresentation> current = rr.users().get(userId).roles().realmLevel().listAll();
        boolean has = current != null && current.stream().anyMatch(r -> roleName.equalsIgnoreCase(r.getName()));
        if (!has) {
            rr.users().get(userId).roles().realmLevel().add(List.of(role));
        }
    }

    private void resetTemporaryPasswordInternal(RealmResource rr, String userId, String tempPassword) {
        resetPasswordInternal(rr, userId, tempPassword, true);
    }

    private void resetPasswordInternal(RealmResource rr, String userId, String tempPassword, boolean temporary) {
        var cred = new org.keycloak.representations.idm.CredentialRepresentation();
        cred.setType(org.keycloak.representations.idm.CredentialRepresentation.PASSWORD);
        cred.setTemporary(temporary);
        cred.setValue(tempPassword);
        rr.users().get(userId).resetPassword(cred);
    }

    private <T> T executeWithRetry(String operation, Callable<T> action) {
        long backoffMillis = INITIAL_BACKOFF_MILLIS;
        RuntimeException lastRetryable = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return action.call();
            } catch (RuntimeException ex) {
                if (!isRetryable(ex) || attempt == MAX_ATTEMPTS) {
                    throw ex;
                }
                lastRetryable = ex;
                log.warn(
                        "Keycloak admin operation '{}' failed on attempt {}/{}; retrying in {} ms",
                        operation,
                        attempt,
                        MAX_ATTEMPTS,
                        backoffMillis,
                        ex
                );
                sleep(backoffMillis);
                backoffMillis *= 2;
            } catch (Exception ex) {
                throw new IllegalStateException("Keycloak admin operation failed: " + operation, ex);
            }
        }

        throw lastRetryable == null
                ? new IllegalStateException("Keycloak admin operation failed without a retryable exception: " + operation)
                : lastRetryable;
    }

    private boolean isRetryable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConnectException
                    || current instanceof SocketTimeoutException
                    || current instanceof NoRouteToHostException
                    || current instanceof ProcessingException) {
                return true;
            }
            String className = current.getClass().getName();
            if ("org.apache.hc.client5.http.HttpHostConnectException".equals(className)
                    || "org.apache.http.conn.HttpHostConnectException".equals(className)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void sleep(long backoffMillis) {
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry Keycloak admin operation", ex);
        }
    }

    private static String extractCreatedId(Response resp) {
        String loc = resp.getHeaderString("Location");
        if (!StringUtils.hasText(loc)) {
            throw new IllegalStateException("Keycloak did not return Location header for created user");
        }
        int idx = loc.lastIndexOf('/');
        if (idx < 0 || idx == loc.length() - 1) {
            throw new IllegalStateException("Unexpected Location header: " + loc);
        }
        return loc.substring(idx + 1);
    }
}
