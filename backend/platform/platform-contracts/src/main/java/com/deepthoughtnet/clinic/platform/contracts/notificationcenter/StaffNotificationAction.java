package com.deepthoughtnet.clinic.platform.contracts.notificationcenter;

public record StaffNotificationAction(
        String label,
        String routeKey,
        String targetId
) {
    public static StaffNotificationAction of(String label, String routeKey, String targetId) {
        return new StaffNotificationAction(label, routeKey, targetId);
    }
}
