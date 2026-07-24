package com.deepthoughtnet.clinic.notificationcenter.service;

import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.NotificationAudience;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.NotificationAudienceType;
import com.deepthoughtnet.clinic.platform.security.RolePermissionMappings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class NotificationCenterAudienceResolver {
    private final TenantUserManagementService tenantUserManagementService;

    public NotificationCenterAudienceResolver(TenantUserManagementService tenantUserManagementService) {
        this.tenantUserManagementService = tenantUserManagementService;
    }

    public List<ResolvedAudienceRecipient> resolve(UUID tenantId, List<NotificationAudience> audiences) {
        if (tenantId == null || audiences == null || audiences.isEmpty()) {
            return List.of();
        }

        List<TenantUserRecord> users = tenantUserManagementService.list(tenantId).stream()
                .filter(this::isActive)
                .toList();
        if (users.isEmpty()) {
            return List.of();
        }

        Map<UUID, ResolvedAudienceRecipientBuilder> resolved = new LinkedHashMap<>();
        for (NotificationAudience audience : audiences) {
            if (audience == null || audience.type() == null) {
                continue;
            }
            switch (audience.type()) {
                case USER -> resolveUsers(users, audience.values(), resolved, audienceLabel(audience));
                case PERMISSION -> resolvePermissions(users, audience.values(), resolved, audienceLabel(audience));
                case ROLE -> resolveRoles(users, audience.values(), resolved, audienceLabel(audience));
                case TENANT_ADMIN -> resolveRoles(users, List.of("TENANT_ADMIN", "ADMIN", "CLINIC_ADMIN"), resolved, audienceLabel(audience));
                case PLATFORM_ADMIN -> resolveRoles(users, List.of("PLATFORM_ADMIN", "PLATFORM_TENANT_SUPPORT"), resolved, audienceLabel(audience));
            }
        }

        return resolved.values().stream()
                .map(ResolvedAudienceRecipientBuilder::build)
                .sorted(Comparator
                        .comparing(ResolvedAudienceRecipient::displayName, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(recipient -> recipient.appUserId().toString()))
                .toList();
    }

    private void resolveUsers(List<TenantUserRecord> users, List<String> values, Map<UUID, ResolvedAudienceRecipientBuilder> resolved, String audienceLabel) {
        if (values == null || values.isEmpty()) {
            return;
        }
        Set<UUID> targetIds = values.stream()
                .map(this::parseUuid)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        for (TenantUserRecord user : users) {
            if (targetIds.contains(user.appUserId())) {
                addRecipient(resolved, user, audienceLabel);
            }
        }
    }

    private void resolvePermissions(List<TenantUserRecord> users, List<String> values, Map<UUID, ResolvedAudienceRecipientBuilder> resolved, String audienceLabel) {
        if (values == null || values.isEmpty()) {
            return;
        }
        Set<String> requestedPermissions = values.stream()
                .filter(this::hasText)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        if (requestedPermissions.isEmpty()) {
            return;
        }
        for (TenantUserRecord user : users) {
            Set<String> permissions = RolePermissionMappings.permissionsForRole(user.membershipRole());
            if (permissions.stream().map(permission -> permission.trim().toLowerCase(Locale.ROOT)).anyMatch(requestedPermissions::contains)) {
                addRecipient(resolved, user, audienceLabel);
            }
        }
    }

    private void resolveRoles(List<TenantUserRecord> users, List<String> values, Map<UUID, ResolvedAudienceRecipientBuilder> resolved, String audienceLabel) {
        if (values == null || values.isEmpty()) {
            return;
        }
        Set<String> requestedRoles = values.stream()
                .filter(this::hasText)
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        if (requestedRoles.isEmpty()) {
            return;
        }
        for (TenantUserRecord user : users) {
            String role = normalizeRole(user.membershipRole());
            if (requestedRoles.contains(role)) {
                addRecipient(resolved, user, audienceLabel);
            }
        }
    }

    private void addRecipient(Map<UUID, ResolvedAudienceRecipientBuilder> resolved, TenantUserRecord user, String audienceLabel) {
        resolved.computeIfAbsent(user.appUserId(), id -> new ResolvedAudienceRecipientBuilder(user))
                .addAudience(audienceLabel);
    }

    private boolean isActive(TenantUserRecord user) {
        return user != null
                && "ACTIVE".equalsIgnoreCase(user.userStatus())
                && "ACTIVE".equalsIgnoreCase(user.membershipStatus());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeRole(String role) {
        return role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
    }

    private UUID parseUuid(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String audienceLabel(NotificationAudience audience) {
        NotificationAudienceType type = audience.type();
        if (type == null) {
            return "UNKNOWN";
        }
        if (type == NotificationAudienceType.USER) {
            return "USER";
        }
        if (type == NotificationAudienceType.PERMISSION) {
            return "PERMISSION";
        }
        if (type == NotificationAudienceType.ROLE) {
            return "ROLE";
        }
        return type.name();
    }

    public record ResolvedAudienceRecipient(
            UUID appUserId,
            String displayName,
            String role,
            String matchedAudience
    ) {
    }

    private static final class ResolvedAudienceRecipientBuilder {
        private final UUID appUserId;
        private final String displayName;
        private final String role;
        private final List<String> audiences = new ArrayList<>();

        private ResolvedAudienceRecipientBuilder(TenantUserRecord user) {
            this.appUserId = user.appUserId();
            this.displayName = user.displayName();
            this.role = user.membershipRole();
        }

        private void addAudience(String audience) {
            if (audience != null && !audience.isBlank() && !audiences.contains(audience)) {
                audiences.add(audience);
            }
        }

        private ResolvedAudienceRecipient build() {
            return new ResolvedAudienceRecipient(appUserId, displayName, role, String.join(",", audiences));
        }
    }
}
