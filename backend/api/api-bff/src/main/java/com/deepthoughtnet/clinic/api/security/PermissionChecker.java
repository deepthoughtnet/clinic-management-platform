package com.deepthoughtnet.clinic.api.security;

import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.security.PermissionEvaluator;
import com.deepthoughtnet.clinic.platform.security.RolePermissionEvaluator;
import com.deepthoughtnet.clinic.platform.security.Roles;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("permissionChecker")
public class PermissionChecker {
    private static final Logger log = LoggerFactory.getLogger(PermissionChecker.class);
    private final PermissionEvaluator permissionEvaluator;

    public PermissionChecker() {
        this(new RolePermissionEvaluator());
    }

    public PermissionChecker(PermissionEvaluator permissionEvaluator) {
        this.permissionEvaluator = permissionEvaluator;
    }

    public boolean hasPermission(String permission) {
        Set<String> roles = currentRoles();
        if (roles.isEmpty()) {
            log.debug("RBAC denied permission={} because no roles were available", permission);
            return false;
        }

        try {
            return permissionEvaluator.hasPermission(roles, permission);
        } catch (RuntimeException ex) {
            log.warn("RBAC evaluation failed for permission={} roles={}", permission, roles, ex);
            return false;
        }
    }

    public boolean hasAnyPermission(String... permissions) {
        if (permissions == null) {
            return false;
        }
        for (String permission : permissions) {
            if (hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasRole(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        return currentRoles().contains(role.trim().toUpperCase(Locale.ROOT));
    }

    public Set<String> currentPermissions() {
        return permissionsForRoles(currentRoles());
    }

    public Set<String> permissionsForRoles(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }

        if (permissionEvaluator instanceof RolePermissionEvaluator rolePermissionEvaluator) {
            return rolePermissionEvaluator.permissionsForRoles(roles);
        }

        return Set.of();
    }

    private Set<String> currentRoles() {
        Set<String> roles = new LinkedHashSet<>();
        RequestContext context = RequestContextHolder.get();
        if (context != null) {
            addRole(roles, context.tenantRole());
            if (context.tokenRoles() != null) {
                context.tokenRoles().forEach(role -> addRole(roles, role));
            }
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
            if (authorities != null) {
                authorities.stream().map(GrantedAuthority::getAuthority).forEach(authority -> addRole(roles, authority));
            }
        }

        if (context != null && context.tenantId() != null && roles.contains(Roles.PLATFORM_ADMIN)) {
            addRole(roles, Roles.PLATFORM_TENANT_SUPPORT);
        }

        return roles;
    }

    private void addRole(Set<String> roles, String role) {
        if (role == null || role.isBlank()) {
            return;
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        roles.add(normalized.startsWith("ROLE_") ? normalized.substring(5) : normalized);
    }
}
