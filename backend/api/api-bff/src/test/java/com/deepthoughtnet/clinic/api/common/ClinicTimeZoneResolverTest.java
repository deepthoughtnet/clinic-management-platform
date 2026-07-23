package com.deepthoughtnet.clinic.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.carepilot.notificationsettings.service.TenantNotificationSettingsService;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.service.model.NotificationSettingsRecord;
import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import com.deepthoughtnet.clinic.identity.service.model.PlatformTenantRecord;
import com.deepthoughtnet.clinic.identity.service.model.TenantModulesRecord;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClinicTimeZoneResolverTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID OTHER_TENANT_ID = UUID.randomUUID();

    @Test
    void demoClinicUtcIsNormalizedToIndiaTime() {
        TenantNotificationSettingsService settingsService = mock(TenantNotificationSettingsService.class);
        PlatformTenantManagementService tenantService = mock(PlatformTenantManagementService.class);
        when(settingsService.findByTenantId(TENANT_ID)).thenReturn(Optional.of(notificationSettings("UTC")));
        when(tenantService.get(TENANT_ID)).thenReturn(demoTenant());

        ClinicTimeZoneResolver resolver = new ClinicTimeZoneResolver(settingsService, tenantService);

        assertThat(resolver.resolve(TENANT_ID).getId()).isEqualTo("Asia/Kolkata");
        assertThat(resolver.resolve(TENANT_ID, "UTC").getId()).isEqualTo("Asia/Kolkata");
        assertThat(resolver.normalizeForPersistence(TENANT_ID, "UTC")).isEqualTo("Asia/Kolkata");
    }

    @Test
    void demoClinicNameMatchIsNormalizedToIndiaTime() {
        TenantNotificationSettingsService settingsService = mock(TenantNotificationSettingsService.class);
        PlatformTenantManagementService tenantService = mock(PlatformTenantManagementService.class);
        when(settingsService.findByTenantId(TENANT_ID)).thenReturn(Optional.of(notificationSettings("UTC")));
        when(tenantService.get(TENANT_ID)).thenReturn(namedDemoTenant());

        ClinicTimeZoneResolver resolver = new ClinicTimeZoneResolver(settingsService, tenantService);

        assertThat(resolver.resolve(TENANT_ID).getId()).isEqualTo("Asia/Kolkata");
        assertThat(resolver.normalizeForPersistence(TENANT_ID, "UTC")).isEqualTo("Asia/Kolkata");
    }

    @Test
    void nonDemoClinicKeepsConfiguredTimezone() {
        TenantNotificationSettingsService settingsService = mock(TenantNotificationSettingsService.class);
        PlatformTenantManagementService tenantService = mock(PlatformTenantManagementService.class);
        when(settingsService.findByTenantId(OTHER_TENANT_ID)).thenReturn(Optional.of(notificationSettings("UTC")));
        when(tenantService.get(OTHER_TENANT_ID)).thenReturn(otherTenant());

        ClinicTimeZoneResolver resolver = new ClinicTimeZoneResolver(settingsService, tenantService);

        assertThat(resolver.resolve(OTHER_TENANT_ID).getId()).isEqualTo("UTC");
        assertThat(resolver.normalizeForPersistence(OTHER_TENANT_ID, "UTC")).isEqualTo("UTC");
    }

    private static NotificationSettingsRecord notificationSettings(String timezone) {
        return new NotificationSettingsRecord(
                UUID.randomUUID(),
                TENANT_ID,
                true,
                false,
                false,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                null,
                null,
                timezone,
                null,
                null,
                false,
                true,
                true,
                5,
                "{}",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null,
                null
        );
    }

    private static PlatformTenantRecord demoTenant() {
        return new PlatformTenantRecord(
                TENANT_ID,
                "demo-clinic",
                "Demo Clinic",
                "TRIAL",
                "ACTIVE",
                true,
                new TenantModulesRecord(true, false, false, true, true, true, false, false, false, false),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private static PlatformTenantRecord namedDemoTenant() {
        return new PlatformTenantRecord(
                TENANT_ID,
                "curapilot-demo",
                "Arogia Demo Clinic",
                "TRIAL",
                "ACTIVE",
                true,
                new TenantModulesRecord(true, false, false, true, true, true, false, false, false, false),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }

    private static PlatformTenantRecord otherTenant() {
        return new PlatformTenantRecord(
                OTHER_TENANT_ID,
                "other-clinic",
                "Other Clinic",
                "TRIAL",
                "ACTIVE",
                true,
                new TenantModulesRecord(true, false, false, true, true, true, false, false, false, false),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }
}
