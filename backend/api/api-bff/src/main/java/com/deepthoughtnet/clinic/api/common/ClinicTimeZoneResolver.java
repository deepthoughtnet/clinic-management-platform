package com.deepthoughtnet.clinic.api.common;

import com.deepthoughtnet.clinic.carepilot.notificationsettings.service.TenantNotificationSettingsService;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ClinicTimeZoneResolver {
    private final TenantNotificationSettingsService notificationSettingsService;

    public ClinicTimeZoneResolver(TenantNotificationSettingsService notificationSettingsService) {
        this.notificationSettingsService = notificationSettingsService;
    }

    public ZoneId resolve(UUID tenantId) {
        if (tenantId == null) {
            return ZoneOffset.UTC;
        }
        return notificationSettingsService.findByTenantId(tenantId)
                .map(settings -> settings.timezone())
                .filter(StringUtils::hasText)
                .map(timezone -> {
                    try {
                        return ZoneId.of(timezone.trim());
                    } catch (Exception ignored) {
                        return ZoneOffset.UTC;
                    }
                })
                .orElse(ZoneOffset.UTC);
    }
}
