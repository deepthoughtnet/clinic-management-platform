package com.deepthoughtnet.clinic.carepilot.notificationsettings.service.model;

import java.util.List;

/**
 * Composite view containing settings and computed readiness warnings.
 */
public record NotificationSettingsView(
        NotificationSettingsRecord settings,
        boolean emailReady,
        boolean smsReady,
        boolean whatsappReady,
        List<String> warnings
) {
}
