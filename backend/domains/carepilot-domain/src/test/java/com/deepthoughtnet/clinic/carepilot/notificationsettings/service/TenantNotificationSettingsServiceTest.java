package com.deepthoughtnet.clinic.carepilot.notificationsettings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.db.TenantNotificationSettingsEntity;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.db.TenantNotificationSettingsRepository;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.model.NotificationChannelPreference;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.service.model.NotificationSettingsUpdateCommand;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TenantNotificationSettingsServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    private TenantNotificationSettingsRepository repository;
    private TenantNotificationSettingsService service;

    @BeforeEach
    void setUp() {
        repository = mock(TenantNotificationSettingsRepository.class);
        service = new TenantNotificationSettingsService(repository);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void getDefaultsCreatesRowWhenMissing() {
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());

        var record = service.getOrCreate(tenantId);

        assertThat(record.tenantId()).isEqualTo(tenantId);
        assertThat(record.emailEnabled()).isTrue();
        assertThat(record.allowMarketingMessages()).isFalse();
        assertThat(record.requirePatientConsent()).isTrue();
    }

    @Test
    void updateRejectsDefaultChannelWhenDisabled() {
        assertThatThrownBy(() -> service.update(tenantId, command(false, false, false, false, NotificationChannelPreference.EMAIL, null, false), actorId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("defaultChannel must be enabled");
    }

    @Test
    void updateRejectsInvalidQuietHours() {
        assertThatThrownBy(() -> service.update(tenantId, command(true, false, false, true, NotificationChannelPreference.EMAIL, NotificationChannelPreference.SMS, true), actorId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fallbackChannel must be enabled");

        NotificationSettingsUpdateCommand invalidQuiet = new NotificationSettingsUpdateCommand(
                true, false, false, true,
                true, true, true, true, true, true, true, true, true, true,
                true, null, null, "UTC", NotificationChannelPreference.EMAIL, null,
                false, true, true, 5
        );
        assertThatThrownBy(() -> service.update(tenantId, invalidQuiet, actorId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quietHoursStart and quietHoursEnd");
    }

    @Test
    void warningsIncludeProviderReadinessAndTimezone() {
        var record = TenantNotificationSettingsEntity.createDefault(tenantId, actorId);
        record.updateFrom(
                true, true, true, true,
                true, true, true, true, true, true, true, true, true, true,
                true, LocalTime.of(22, 0), LocalTime.of(6, 0), "UTC",
                NotificationChannelPreference.EMAIL, NotificationChannelPreference.SMS,
                false, true, true, 3, actorId
        );

        var warnings = service.computeWarnings(service.toRecordForTest(record), false, false, false);
        assertThat(warnings).anyMatch(s -> s.contains("Email enabled"));
        assertThat(warnings).anyMatch(s -> s.contains("SMS enabled"));
        assertThat(warnings).anyMatch(s -> s.contains("WhatsApp enabled"));
    }

    @Test
    void resolveEffectiveChannelUsesFallbackWhenRequestedDisabled() {
        TenantNotificationSettingsEntity row = TenantNotificationSettingsEntity.createDefault(tenantId, actorId);
        row.updateFrom(
                false, true, false, true,
                true, true, true, true, true, true, true, true, true, true,
                false, null, null, "UTC",
                NotificationChannelPreference.SMS, NotificationChannelPreference.IN_APP,
                false, true, true, 4, actorId
        );
        var settings = service.toRecordForTest(row);

        ChannelType resolved = service.resolveEffectiveChannel(settings, ChannelType.EMAIL);
        assertThat(resolved).isEqualTo(ChannelType.IN_APP);
    }

    @Test
    void applyQuietHoursDefersToWindowEnd() {
        TenantNotificationSettingsEntity row = TenantNotificationSettingsEntity.createDefault(tenantId, actorId);
        row.updateFrom(
                true, false, false, true,
                true, true, true, true, true, true, true, true, true, true,
                true, LocalTime.of(22, 0), LocalTime.of(6, 0), "UTC",
                NotificationChannelPreference.EMAIL, NotificationChannelPreference.IN_APP,
                false, true, true, 4, actorId
        );
        var settings = service.toRecordForTest(row);

        OffsetDateTime scheduled = OffsetDateTime.parse("2026-05-14T23:00:00Z");
        OffsetDateTime adjusted = service.applyQuietHours(settings, scheduled);

        assertThat(adjusted).isAfter(scheduled);
        assertThat(adjusted.getHour()).isEqualTo(6);
    }

    private NotificationSettingsUpdateCommand command(
            boolean emailEnabled,
            boolean smsEnabled,
            boolean whatsappEnabled,
            boolean inAppEnabled,
            NotificationChannelPreference defaultChannel,
            NotificationChannelPreference fallbackChannel,
            boolean quietHoursEnabled
    ) {
        return new NotificationSettingsUpdateCommand(
                emailEnabled,
                smsEnabled,
                whatsappEnabled,
                inAppEnabled,
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
                quietHoursEnabled,
                quietHoursEnabled ? LocalTime.of(22, 0) : null,
                quietHoursEnabled ? LocalTime.of(6, 0) : null,
                "UTC",
                defaultChannel,
                fallbackChannel,
                false,
                true,
                true,
                5
        );
    }
}
