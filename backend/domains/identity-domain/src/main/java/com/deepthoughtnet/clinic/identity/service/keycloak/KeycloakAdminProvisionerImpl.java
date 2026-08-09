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
import java.util.Locale;
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
import com.deepthoughtnet.clinic.identity.service.TenantIdentityConflictException;

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
            UserRepresentation existing = resolveExistingIdentity(rr, normalizeEmail(email), normalizeUsername(username));
            return existing == null || !StringUtils.hasText(existing.getId()) ? null : existing.getId();
        });
    }

    @Override
    public String createOrGetTenantAdminUserId(UUID tenantId, String email, String displayName, String tempPassword) {
        String normalizedEmail = normalizeEmail(email);
        if (!StringUtils.hasText(normalizedEmail)) {
            throw new IllegalArgumentException("email is required for tenant admin provisioning");
        }

        return executeWithRetry("create or get tenant admin user", () -> {
            RealmResource rr = keycloakAdmin.realm(realm);

            // Try find by email
            List<UserRepresentation> found = rr.users().search(normalizedEmail, true);
            UserRepresentation existing = found == null ? null : found.stream()
                    .filter(u -> sameIgnoreCase(normalizedEmail, u.getEmail()))
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
            u.setEmail(normalizedEmail);
            u.setUsername(normalizedEmail);
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
            String firstName,
            String lastName,
            String displayName,
            String tempPassword,
            boolean emailVerified
    ) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedUsername = normalizeUsername(username);
        if (!StringUtils.hasText(normalizedEmail) && !StringUtils.hasText(normalizedUsername)) {
            throw new IllegalArgumentException("email or username is required");
        }

        return executeWithRetry("create or get tenant user", () -> {
            RealmResource rr = keycloakAdmin.realm(realm);
            List<TenantIdentityConflictException.IdentityConflict> conflicts = collectIdentityConflicts(rr, null, normalizedEmail, normalizedUsername);
            if (!conflicts.isEmpty()) {
                throw TenantIdentityConflictException.of(conflicts);
            }

            // Create new user
            UserRepresentation u = new UserRepresentation();
            u.setEnabled(true);

            if (StringUtils.hasText(normalizedEmail)) {
                u.setEmail(normalizedEmail);
                // By default, use email as username when username not provided
                if (!StringUtils.hasText(normalizedUsername)) {
                    u.setUsername(normalizedEmail);
                }
                u.setEmailVerified(emailVerified ? Boolean.TRUE : Boolean.FALSE);
            }

            if (StringUtils.hasText(normalizedUsername)) {
                u.setUsername(normalizedUsername);
            }

            if (StringUtils.hasText(firstName)) {
                u.setFirstName(firstName);
            } else if (StringUtils.hasText(displayName)) {
                u.setFirstName(displayName);
            }
            if (StringUtils.hasText(lastName)) {
                u.setLastName(lastName);
            }

            try (Response resp = rr.users().create(u)) {
                if (resp.getStatus() == 201) {
                    String userId = extractCreatedId(resp);
                    ensureTenantAttribute(rr, userId, tenantId);

                    if (StringUtils.hasText(tempPassword)) {
                        resetTemporaryPasswordInternal(rr, userId, tempPassword);
                    }

                    return userId;
                }
                if (resp.getStatus() == 409) {
                    List<TenantIdentityConflictException.IdentityConflict> afterConflict = collectIdentityConflicts(rr, null, normalizedEmail, normalizedUsername);
                    if (!afterConflict.isEmpty()) {
                        throw TenantIdentityConflictException.of(afterConflict);
                    }
                    List<TenantIdentityConflictException.IdentityConflict> fallback = new java.util.ArrayList<>();
                    if (StringUtils.hasText(normalizedUsername)) {
                        fallback.add(identityConflictField(TenantIdentityConflictException.Field.USERNAME));
                    }
                    if (StringUtils.hasText(normalizedEmail)) {
                        fallback.add(identityConflictField(TenantIdentityConflictException.Field.EMAIL));
                    }
                    throw TenantIdentityConflictException.of(fallback.isEmpty()
                            ? List.of(identityConflictField(TenantIdentityConflictException.Field.EMAIL))
                            : fallback);
                }
                String msg = resp.getStatusInfo() == null ? "" : resp.getStatusInfo().toString();
                throw new IllegalStateException("Failed to create user in Keycloak. status=" + resp.getStatus() + " " + msg);
            }
        });
    }

    @Override
    public void updateTenantUserIdentity(
            String userId,
            String email,
            String username,
            String firstName,
            String lastName,
            boolean emailVerified
    ) {
        String normalizedUserId = StringUtils.hasText(userId) ? userId.trim() : null;
        String normalizedEmail = normalizeEmail(email);
        String normalizedUsername = normalizeUsername(username);
        if (!StringUtils.hasText(normalizedUserId)) {
            throw new IllegalArgumentException("userId is required");
        }

        executeWithRetry("update tenant user identity", () -> {
            RealmResource rr = keycloakAdmin.realm(realm);
            List<TenantIdentityConflictException.IdentityConflict> conflicts = collectIdentityConflicts(rr, normalizedUserId, normalizedEmail, normalizedUsername);
            if (!conflicts.isEmpty()) {
                throw TenantIdentityConflictException.of(conflicts);
            }

            UserRepresentation existing = rr.users().get(normalizedUserId).toRepresentation();
            if (existing == null || existing.getId() == null) {
                throw new IllegalArgumentException("User not found in Keycloak");
            }

            boolean changed = false;
            if (StringUtils.hasText(normalizedEmail) && !sameIgnoreCase(normalizedEmail, existing.getEmail())) {
                existing.setEmail(normalizedEmail);
                changed = true;
            }
            if (StringUtils.hasText(normalizedUsername) && !sameIgnoreCase(normalizedUsername, existing.getUsername())) {
                existing.setUsername(normalizedUsername);
                changed = true;
            }
            if (StringUtils.hasText(firstName) && !firstName.equals(existing.getFirstName())) {
                existing.setFirstName(firstName);
                changed = true;
            }
            if (StringUtils.hasText(lastName) && !lastName.equals(existing.getLastName())) {
                existing.setLastName(lastName);
                changed = true;
            }
            if (emailVerified && (existing.isEmailVerified() == null || !existing.isEmailVerified())) {
                existing.setEmailVerified(Boolean.TRUE);
                changed = true;
            }

            if (changed) {
                rr.users().get(normalizedUserId).update(existing);
            }
            return null;
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

    private UserRepresentation findExactEmailMatch(RealmResource rr, String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        List<UserRepresentation> found = rr.users().search(email, true);
        List<UserRepresentation> matches = found == null ? List.of() : found.stream()
                .filter(u -> sameIgnoreCase(email, u.getEmail()))
                .toList();
        if (matches.size() > 1) {
            throw TenantIdentityConflictException.of(List.of(identityConflictField(TenantIdentityConflictException.Field.EMAIL)));
        }
        return matches.isEmpty() ? null : matches.getFirst();
    }

    private UserRepresentation findExactUsernameMatch(RealmResource rr, String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        List<UserRepresentation> found = rr.users().search(username, true);
        List<UserRepresentation> matches = found == null ? List.of() : found.stream()
                .filter(u -> sameIgnoreCase(username, u.getUsername()))
                .toList();
        if (matches.size() > 1) {
            throw TenantIdentityConflictException.of(List.of(identityConflictField(TenantIdentityConflictException.Field.USERNAME)));
        }
        return matches.isEmpty() ? null : matches.getFirst();
    }

    private List<TenantIdentityConflictException.IdentityConflict> collectIdentityConflicts(RealmResource rr, String currentUserId, String email, String username) {
        List<TenantIdentityConflictException.IdentityConflict> conflicts = new java.util.ArrayList<>();

        UserRepresentation byEmail = findExactEmailMatch(rr, email);
        if (byEmail != null && !sameUser(currentUserId, byEmail.getId())) {
            conflicts.add(identityConflictField(TenantIdentityConflictException.Field.EMAIL));
        }

        UserRepresentation byUsername = findExactUsernameMatch(rr, username);
        if (byUsername != null && !sameUser(currentUserId, byUsername.getId())) {
            conflicts.add(identityConflictField(TenantIdentityConflictException.Field.USERNAME));
        }

        return conflicts;
    }

    private TenantIdentityConflictException.IdentityConflict identityConflictField(TenantIdentityConflictException.Field field) {
        if (field == TenantIdentityConflictException.Field.USERNAME) {
            return new TenantIdentityConflictException.IdentityConflict(field, "USERNAME_ALREADY_IN_USE", "Login ID already in use.");
        }
        return new TenantIdentityConflictException.IdentityConflict(field, "EMAIL_ALREADY_IN_USE", "This email address is already associated with a Jeevanam account.");
    }

    private UserRepresentation resolveExistingIdentity(RealmResource rr, String email, String username) {
        UserRepresentation byEmail = findExactEmailMatch(rr, email);
        UserRepresentation byUsername = findExactUsernameMatch(rr, username);
        if (byEmail != null && byUsername != null && !sameUser(byEmail.getId(), byUsername.getId())) {
            return null;
        }
        if (byEmail != null) {
            return byEmail;
        }
        return byUsername;
    }

    private String normalizeEmail(String email) {
        return StringUtils.hasText(email) ? email.trim().toLowerCase(Locale.ROOT) : null;
    }

    private String normalizeUsername(String username) {
        return StringUtils.hasText(username) ? username.trim() : null;
    }

    private boolean sameIgnoreCase(String left, String right) {
        if (!StringUtils.hasText(left) && !StringUtils.hasText(right)) {
            return true;
        }
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return false;
        }
        return left.trim().equalsIgnoreCase(right.trim());
    }

    private boolean sameUser(String currentUserId, String candidateUserId) {
        if (!StringUtils.hasText(currentUserId) || !StringUtils.hasText(candidateUserId)) {
            return false;
        }
        return currentUserId.trim().equals(candidateUserId.trim());
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
