package com.deepthoughtnet.clinic.platform.security.kc;

import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.core.security.AuthContextExtractor;
import com.deepthoughtnet.clinic.platform.spring.context.TenantHeaders;

import java.util.*;
import java.util.function.Function;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

public class KeycloakAuthContextExtractor implements AuthContextExtractor {

    private final Function<String, Optional<UUID>> tenantCodeResolver;

    public KeycloakAuthContextExtractor() {
        this(code -> Optional.empty());
    }

    public KeycloakAuthContextExtractor(Function<String, Optional<UUID>> tenantCodeResolver) {
        this.tenantCodeResolver = tenantCodeResolver == null ? code -> Optional.empty() : tenantCodeResolver;
    }

    @Override
    public String keycloakSub() {
        Jwt jwt = jwtOrNull();
        return jwt != null ? jwt.getSubject() : null;
    }

    @Override
    public String email() {
        Jwt jwt = jwtOrNull();
        return jwt != null ? jwt.getClaimAsString("email") : null;
    }

    @Override
    public String displayName() {
        Jwt jwt = jwtOrNull();
        if (jwt == null) return null;
        String name = jwt.getClaimAsString("name");
        if (name != null && !name.isBlank()) return name;
        String preferred = jwt.getClaimAsString("preferred_username");
        return preferred;
    }

    @Override
    public Set<String> rolesUpper() {
        Jwt jwt = jwtOrNull();
        if (jwt == null) return Set.of();

        Set<String> out = new HashSet<>();

        // realm_access.roles
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null) {
            Object rolesObj = realmAccess.get("roles");
            if (rolesObj instanceof Collection<?> c) {
                for (Object r : c) out.add(String.valueOf(r).toUpperCase(Locale.ROOT));
            }
        }

        // optionally: resource_access.<client>.roles (if you use client roles)
        // Map<String, Object> resourceAccess = jwt.getClaim("resource_access");

        return Collections.unmodifiableSet(out);
    }

    @Override
    public TenantId resolveTenantId(String tenantHeaderValue) {
        Jwt jwt = jwtOrNull();
        if (jwt == null) return null;

        // Explicit tenant selection must win over a token claim. The claim can be stale
        // when a user switches clinic context after authentication.
        if (tenantHeaderValue != null && !tenantHeaderValue.isBlank()) {
            return tryParseTenant(tenantHeaderValue);
        }

        // Fallback: tenant_id in token claim for single-tenant sessions.
        Object claim = jwt.getClaim(TenantHeaders.TENANT_CLAIM);
        if (claim != null) {
            TenantId fromClaim = tryParseTenant(String.valueOf(claim));
            if (fromClaim != null) {
                return fromClaim;
            }
        }

        return null;
    }

    private TenantId tryParseTenant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String value = raw.trim();
        try {
            return TenantId.of(UUID.fromString(value));
        } catch (Exception ignored) {
            return tenantCodeResolver.apply(value.toLowerCase(Locale.ROOT))
                    .map(TenantId::of)
                    .orElse(null);
        }
    }

    private static Jwt jwtOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof Jwt jwt) return jwt;
        return null;
    }
}
