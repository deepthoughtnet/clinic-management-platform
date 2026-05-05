package com.deepthoughtnet.clinic.platform.security;

import java.util.Locale;
import java.util.Set;

public class RolePermissionEvaluator implements PermissionEvaluator {
    @Override
    public boolean hasPermission(Set<String> roles, String permission) {
        if (permission == null || permission.isBlank() || roles == null || roles.isEmpty()) {
            return false;
        }

        String normalizedPermission = permission.trim().toLowerCase(Locale.ROOT);
        if (normalizedPermission.startsWith("platform.")) {
            return roles.stream().anyMatch(role -> Roles.PLATFORM_ADMIN.equals(normalizeRole(role)));
        }

        return roles.stream()
                .anyMatch(role -> RolePermissionMappings.roleHasPermission(role, normalizedPermission));
    }

    public Set<String> permissionsForRoles(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }
        return RolePermissionMappings.permissionsForRoles(roles);
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "";
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("ROLE_") ? normalized.substring(5) : normalized;
    }
}
