package com.deepthoughtnet.clinic.platform.contracts.notificationcenter;

import java.util.List;

public record NotificationAudience(
        NotificationAudienceType type,
        List<String> values
) {
    public NotificationAudience {
        values = values == null ? List.of() : List.copyOf(values);
    }

    public static NotificationAudience user(String... userIds) {
        return new NotificationAudience(NotificationAudienceType.USER, userIds == null ? List.of() : List.of(userIds));
    }

    public static NotificationAudience permission(String... permissions) {
        return new NotificationAudience(NotificationAudienceType.PERMISSION, permissions == null ? List.of() : List.of(permissions));
    }

    public static NotificationAudience role(String... roles) {
        return new NotificationAudience(NotificationAudienceType.ROLE, roles == null ? List.of() : List.of(roles));
    }

    public static NotificationAudience tenantAdmin() {
        return new NotificationAudience(NotificationAudienceType.TENANT_ADMIN, List.of());
    }

    public static NotificationAudience platformAdmin() {
        return new NotificationAudience(NotificationAudienceType.PLATFORM_ADMIN, List.of());
    }
}
