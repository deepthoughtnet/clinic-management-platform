package com.deepthoughtnet.clinic.api.common;

import com.deepthoughtnet.clinic.carepilot.notificationsettings.service.model.NotificationSettingsRecord;
import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import com.deepthoughtnet.clinic.identity.service.model.PlatformTenantRecord;
import java.time.ZoneId;
import java.time.ZoneOffset;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.service.TenantNotificationSettingsService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ClinicTimeZoneResolver {
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Kolkata");
    private static final Logger log = LoggerFactory.getLogger(ClinicTimeZoneResolver.class);
    private final TenantNotificationSettingsService notificationSettingsService;
    private final PlatformTenantManagementService tenantManagementService;

    public ClinicTimeZoneResolver(
            TenantNotificationSettingsService notificationSettingsService,
            PlatformTenantManagementService tenantManagementService
    ) {
        this.notificationSettingsService = notificationSettingsService;
        this.tenantManagementService = tenantManagementService;
    }

    public ZoneId resolve(UUID tenantId) {
        if (tenantId == null) {
            return DEFAULT_ZONE;
        }
        String configuredTimezone = notificationSettingsService.findByTenantId(tenantId)
                .map(NotificationSettingsRecord::timezone)
                .orElse(null);
        return resolve(tenantId, configuredTimezone);
    }

    public ZoneId resolve(UUID tenantId, String configuredTimezone) {
        ZoneId resolved = parseZoneId(configuredTimezone);
        if (tenantId != null && isLocalDemoTenant(tenantId) && isUtcLike(resolved)) {
            resolved = DEFAULT_ZONE;
        }
        if (log.isDebugEnabled()) {
            log.debug("clinic-timezone resolved tenantId={} configuredTimezone={} effectiveTimezone={}",
                    tenantId, configuredTimezone, resolved.getId());
        }
        return resolved;
    }

    public String normalizeForPersistence(UUID tenantId, String configuredTimezone) {
        if (!StringUtils.hasText(configuredTimezone)) {
            return DEFAULT_ZONE.getId();
        }
        String trimmed = configuredTimezone.trim();
        if (tenantId != null && isLocalDemoTenant(tenantId) && isUtcLike(trimmed)) {
            return DEFAULT_ZONE.getId();
        }
        return trimmed;
    }

    private ZoneId parseZoneId(String configuredTimezone) {
        if (!StringUtils.hasText(configuredTimezone)) {
            return DEFAULT_ZONE;
        }
        try {
            return ZoneId.of(configuredTimezone.trim());
        } catch (Exception ignored) {
            return DEFAULT_ZONE;
        }
    }

    private boolean isLocalDemoTenant(UUID tenantId) {
        try {
            PlatformTenantRecord tenant = tenantManagementService.get(tenantId);
            if (tenant == null) {
                return false;
            }
            String code = normalizeLabel(tenant.code());
            String name = normalizeLabel(tenant.name());
            return "demo-clinic".equals(code)
                    || name.contains("demo clinic")
                    || name.contains("curapilot")
                    || code.contains("curapilot");
        } catch (Exception ignored) {
            return false;
        }
    }

    private String normalizeLabel(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase().replace('_', '-');
    }

    private boolean isUtcLike(ZoneId zoneId) {
        if (zoneId == null) {
            return false;
        }
        String id = zoneId.getId();
        return ZoneOffset.UTC.equals(zoneId) || "UTC".equalsIgnoreCase(id) || "Etc/UTC".equalsIgnoreCase(id) || "Z".equalsIgnoreCase(id);
    }

    private boolean isUtcLike(String timezone) {
        if (!StringUtils.hasText(timezone)) {
            return false;
        }
        String id = timezone.trim();
        return "UTC".equalsIgnoreCase(id) || "Etc/UTC".equalsIgnoreCase(id) || "Z".equalsIgnoreCase(id);
    }
}
