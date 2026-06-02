package com.deepthoughtnet.clinic.api.patientportal.auth;

import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.core.security.AuthContextExtractor;
import com.deepthoughtnet.clinic.platform.spring.context.TenantHeaders;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

public class PatientPortalAuthContextExtractor implements AuthContextExtractor {

    private final Function<String, Optional<UUID>> tenantCodeResolver;

    public PatientPortalAuthContextExtractor(Function<String, Optional<UUID>> tenantCodeResolver) {
        this.tenantCodeResolver = tenantCodeResolver == null ? code -> Optional.empty() : tenantCodeResolver;
    }

    @Override
    public String keycloakSub() {
        Object principal = currentPrincipal();
        if (principal instanceof PatientPortalSessionPrincipal patientPrincipal) {
            return patientPrincipal.subject();
        }
        Jwt jwt = jwtOrNull(principal);
        return jwt != null ? jwt.getSubject() : null;
    }

    @Override
    public String email() {
        Object principal = currentPrincipal();
        Jwt jwt = jwtOrNull(principal);
        return jwt != null ? jwt.getClaimAsString("email") : null;
    }

    @Override
    public String displayName() {
        Object principal = currentPrincipal();
        if (principal instanceof PatientPortalSessionPrincipal patientPrincipal) {
            return patientPrincipal.displayName();
        }
        Jwt jwt = jwtOrNull(principal);
        if (jwt == null) {
            return null;
        }
        String name = jwt.getClaimAsString("name");
        if (name != null && !name.isBlank()) {
            return name;
        }
        return jwt.getClaimAsString("preferred_username");
    }

    @Override
    public Set<String> rolesUpper() {
        Object principal = currentPrincipal();
        if (principal instanceof PatientPortalSessionPrincipal patientPrincipal) {
            return patientPrincipal.roles() == null ? Set.of() : patientPrincipal.roles();
        }

        Jwt jwt = jwtOrNull(principal);
        if (jwt == null) {
            return Set.of();
        }

        Set<String> out = new HashSet<>();
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null) {
            Object rolesObj = realmAccess.get("roles");
            if (rolesObj instanceof Collection<?> collection) {
                for (Object role : collection) {
                    out.add(String.valueOf(role).toUpperCase(Locale.ROOT));
                }
            }
        }
        return Collections.unmodifiableSet(out);
    }

    @Override
    public TenantId resolveTenantId(String tenantHeaderValue) {
        Object principal = currentPrincipal();
        if (principal instanceof PatientPortalSessionPrincipal patientPrincipal) {
            if (tenantHeaderValue != null && !tenantHeaderValue.isBlank()) {
                TenantId headerTenant = tryParseTenant(tenantHeaderValue);
                if (headerTenant != null && patientPrincipal.tenantId().equals(headerTenant.value())) {
                    return headerTenant;
                }
            }
            return patientPrincipal.tenantId() == null ? null : TenantId.of(patientPrincipal.tenantId());
        }

        Jwt jwt = jwtOrNull(principal);
        if (jwt == null) {
            return null;
        }
        if (tenantHeaderValue != null && !tenantHeaderValue.isBlank()) {
            return tryParseTenant(tenantHeaderValue);
        }

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

    private Object currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getPrincipal();
    }

    private Jwt jwtOrNull(Object principal) {
        return principal instanceof Jwt jwt ? jwt : null;
    }
}
