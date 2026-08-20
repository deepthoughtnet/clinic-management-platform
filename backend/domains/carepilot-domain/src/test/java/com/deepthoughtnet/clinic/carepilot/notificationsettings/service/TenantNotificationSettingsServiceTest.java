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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TenantNotificationSettingsServiceTest {
    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private TenantNotificationSettingsRepository repository;
    private TenantNotificationSettingsService service;

    @BeforeEach
    void setUp() {
        repository = mock(TenantNotificationSettingsRepository.class);
        service = new TenantNotificationSettingsService(repository, new ObjectMapper());
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
    void readyEnabledDefaultChannelIsAccepted() {
        NotificationSettingsUpdateCommand command = command(
                true, true, false, true,
                NotificationChannelPreference.EMAIL,
                null,
                false,
                null,
                null,
                null,
                "{}"
        );

        when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());

        var updated = service.update(tenantId, command, actorId, true, true, false);

        assertThat(updated.defaultChannel()).isEqualTo(NotificationChannelPreference.EMAIL);
    }

    @Test
    void disabledDefaultChannelIsRejected() {
        NotificationSettingsUpdateCommand command = command(
                true, false, false, true,
                NotificationChannelPreference.SMS,
                null,
                false,
                null,
                null,
                null,
                "{}"
        );

        assertThatThrownBy(() -> service.update(tenantId, command, actorId, true, false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Default channel must be enabled and ready for use.");
    }

    @Test
    void unconfiguredDefaultChannelIsRejected() {
        NotificationSettingsUpdateCommand command = command(
                true, false, false, true,
                NotificationChannelPreference.EMAIL,
                null,
                false,
                null,
                null,
                null,
                "{}"
        );

        assertThatThrownBy(() -> service.update(tenantId, command, actorId, false, false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Default channel must be enabled and ready for use.");
    }

    @Test
    void readyEnabledFallbackChannelIsAccepted() {
        NotificationSettingsUpdateCommand command = command(
                true, true, false, true,
                NotificationChannelPreference.EMAIL,
                NotificationChannelPreference.SMS,
                false,
                null,
                null,
                null,
                "{}"
        );

        when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());

        var updated = service.update(tenantId, command, actorId, true, true, false);

        assertThat(updated.fallbackChannel()).isEqualTo(NotificationChannelPreference.SMS);
    }

    @Test
    void noneIsAllowedAsFallback() {
        NotificationSettingsUpdateCommand command = command(
                true, true, false, true,
                NotificationChannelPreference.EMAIL,
                null,
                false,
                null,
                null,
                null,
                "{}"
        );

        when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());

        var updated = service.update(tenantId, command, actorId, true, true, false);

        assertThat(updated.fallbackChannel()).isNull();
    }

    @Test
    void disabledFallbackChannelIsRejected() {
        NotificationSettingsUpdateCommand command = command(
                true, false, false, true,
                NotificationChannelPreference.EMAIL,
                NotificationChannelPreference.SMS,
                false,
                null,
                null,
                null,
                "{}"
        );

        assertThatThrownBy(() -> service.update(tenantId, command, actorId, true, false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Fallback channel must be enabled and ready for use.");
    }

    @Test
    void unconfiguredFallbackChannelIsRejected() {
        NotificationSettingsUpdateCommand command = command(
                true, true, false, true,
                NotificationChannelPreference.EMAIL,
                NotificationChannelPreference.WHATSAPP,
                false,
                null,
                null,
                null,
                "{}"
        );

        assertThatThrownBy(() -> service.update(tenantId, command, actorId, true, true, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Fallback channel must be enabled and ready for use.");
    }

    @Test
    void disabledQuietHoursDoNotRequireActiveScheduleValidation() {
        NotificationSettingsUpdateCommand command = command(
                true, true, false, true,
                NotificationChannelPreference.EMAIL,
                null,
                false,
                null,
                null,
                null,
                quietHoursPolicyJson(List.of(), null, null)
        );

        when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());

        var updated = service.update(tenantId, command, actorId, true, true, false);

        assertThat(updated.quietHoursEnabled()).isFalse();
    }

    @Test
    void enabledQuietHoursWithoutTimezoneIsRejected() {
        NotificationSettingsUpdateCommand command = command(
                true, true, false, true,
                NotificationChannelPreference.EMAIL,
                null,
                true,
                null,
                LocalTime.of(22, 0),
                LocalTime.of(6, 0),
                quietHoursPolicyJson(List.of("MONDAY"), null, null)
        );

        assertThatThrownBy(() -> service.update(tenantId, command, actorId, true, true, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Timezone is required when quiet hours are enabled.");
    }

    @Test
    void enabledQuietHoursWithoutStartIsRejected() {
        NotificationSettingsUpdateCommand command = command(
                true, true, false, true,
                NotificationChannelPreference.EMAIL,
                null,
                true,
                "UTC",
                null,
                LocalTime.of(6, 0),
                quietHoursPolicyJson(List.of("MONDAY"), null, null)
        );

        assertThatThrownBy(() -> service.update(tenantId, command, actorId, true, true, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Start time is required when quiet hours are enabled.");
    }

    @Test
    void enabledQuietHoursWithoutEndIsRejected() {
        NotificationSettingsUpdateCommand command = command(
                true, true, false, true,
                NotificationChannelPreference.EMAIL,
                null,
                true,
                "UTC",
                LocalTime.of(22, 0),
                null,
                quietHoursPolicyJson(List.of("MONDAY"), null, null)
        );

        assertThatThrownBy(() -> service.update(tenantId, command, actorId, true, true, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("End time is required when quiet hours are enabled.");
    }

    @Test
    void enabledQuietHoursWithoutWeekdayIsRejected() {
        NotificationSettingsUpdateCommand command = command(
                true, true, false, true,
                NotificationChannelPreference.EMAIL,
                null,
                true,
                "UTC",
                LocalTime.of(22, 0),
                LocalTime.of(6, 0),
                quietHoursPolicyJson(List.of(), null, null)
        );

        assertThatThrownBy(() -> service.update(tenantId, command, actorId, true, true, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Select at least one weekday when quiet hours are enabled.");
    }

    @Test
    void enabledQuietHoursWithInvalidDateRangeIsRejected() {
        NotificationSettingsUpdateCommand command = command(
                true, true, false, true,
                NotificationChannelPreference.EMAIL,
                null,
                true,
                "UTC",
                LocalTime.of(22, 0),
                LocalTime.of(6, 0),
                quietHoursPolicyJson(List.of("MONDAY"), LocalDate.of(2026, 8, 20).toString(), LocalDate.of(2026, 8, 19).toString())
        );

        assertThatThrownBy(() -> service.update(tenantId, command, actorId, true, true, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Effective From must be on or before Effective Until.");
    }

    @Test
    void validSameDayWindowIsAccepted() {
        TenantNotificationSettingsEntity row = TenantNotificationSettingsEntity.createDefault(tenantId, actorId);
        row.updateFrom(
                true, false, false, true,
                true, true, true, true, true, true, true, true, true, true,
                true, LocalTime.of(9, 0), LocalTime.of(17, 0), "UTC",
                NotificationChannelPreference.EMAIL, NotificationChannelPreference.IN_APP,
                false, true, true, 4,
                quietHoursPolicyJson(List.of("WEDNESDAY"), null, null),
                actorId
        );
        var settings = service.toRecordForTest(row);

        OffsetDateTime scheduled = OffsetDateTime.parse("2026-08-19T10:00:00Z");
        OffsetDateTime adjusted = service.applyQuietHours(settings, scheduled);

        assertThat(adjusted).isAfter(scheduled);
        assertThat(adjusted.getHour()).isEqualTo(17);
    }

    @Test
    void validOvernightWindowIsAccepted() {
        TenantNotificationSettingsEntity row = TenantNotificationSettingsEntity.createDefault(tenantId, actorId);
        row.updateFrom(
                true, false, false, true,
                true, true, true, true, true, true, true, true, true, true,
                true, LocalTime.of(20, 0), LocalTime.of(8, 0), "UTC",
                NotificationChannelPreference.EMAIL, NotificationChannelPreference.IN_APP,
                false, true, true, 4,
                quietHoursPolicyJson(List.of("WEDNESDAY"), null, null),
                actorId
        );
        var settings = service.toRecordForTest(row);

        OffsetDateTime scheduled = OffsetDateTime.parse("2026-08-19T23:00:00Z");
        OffsetDateTime adjusted = service.applyQuietHours(settings, scheduled);

        assertThat(adjusted).isAfter(scheduled);
        assertThat(adjusted.getHour()).isEqualTo(8);
    }

    @Test
    void criticalAlertsBypassQuietHours() {
        TenantNotificationSettingsEntity row = TenantNotificationSettingsEntity.createDefault(tenantId, actorId);
        row.updateFrom(
                true, false, false, true,
                true, true, true, true, true, true, true, true, true, true,
                true, LocalTime.of(20, 0), LocalTime.of(8, 0), "UTC",
                NotificationChannelPreference.EMAIL, NotificationChannelPreference.IN_APP,
                false, true, true, 4,
                quietHoursPolicyJson(List.of("WEDNESDAY"), null, null),
                actorId
        );
        var settings = service.toRecordForTest(row);

        OffsetDateTime scheduled = OffsetDateTime.parse("2026-08-19T23:00:00Z");
        OffsetDateTime adjusted = service.applyQuietHours(settings, scheduled, true);

        assertThat(adjusted).isEqualTo(scheduled);
    }

    @Test
    void warningsIncludeProviderReadinessAndInvalidRouting() {
        var row = TenantNotificationSettingsEntity.createDefault(tenantId, actorId);
        row.updateFrom(
                true, true, true, true,
                true, true, true, true, true, true, true, true, true, true,
                true, LocalTime.of(22, 0), LocalTime.of(6, 0), "UTC",
                NotificationChannelPreference.EMAIL, NotificationChannelPreference.SMS,
                false, true, true, 3,
                quietHoursPolicyJson(List.of("MONDAY"), null, null),
                actorId
        );

        var warnings = service.computeWarnings(service.toRecordForTest(row), false, false, false);
        assertThat(warnings).anyMatch(s -> s.contains("Email enabled"));
        assertThat(warnings).anyMatch(s -> s.contains("SMS enabled"));
        assertThat(warnings).anyMatch(s -> s.contains("WhatsApp enabled"));
        assertThat(warnings).anyMatch(s -> s.contains("Current fallback channel is unavailable"));
    }

    @Test
    void resolveEffectiveChannelUsesReadyFallbackWhenRequestedDisabled() {
        TenantNotificationSettingsEntity row = TenantNotificationSettingsEntity.createDefault(tenantId, actorId);
        row.updateFrom(
                false, true, false, true,
                true, true, true, true, true, true, true, true, true, true,
                false, null, null, "UTC",
                NotificationChannelPreference.SMS, NotificationChannelPreference.IN_APP,
                false, true, true, 4,
                quietHoursPolicyJson(List.of("MONDAY"), null, null),
                actorId
        );
        var settings = service.toRecordForTest(row);

        ChannelType resolved = service.resolveEffectiveChannel(settings, ChannelType.EMAIL, true, false, true);
        assertThat(resolved).isEqualTo(ChannelType.IN_APP);
    }

    private static Stream<Arguments> rateLimitFields() {
        return Stream.of(
                Arguments.of("overallMessagesPerDay", "Overall messages/day"),
                Arguments.of("marketingPerDay", "Marketing/day"),
                Arguments.of("reminderPerDay", "Reminder/day"),
                Arguments.of("maximumPerHour", "Maximum/hour"),
                Arguments.of("perPatientPerDay", "Per patient/day")
        );
    }

    @ParameterizedTest(name = "{1} accepts positive integers")
    @MethodSource("rateLimitFields")
    void acceptsPositiveIntegerForEachRateLimitField(String fieldName, String label) {
        NotificationSettingsUpdateCommand command = command(
                true, true, false, true,
                NotificationChannelPreference.EMAIL,
                NotificationChannelPreference.IN_APP,
                false,
                null,
                null,
                null,
                policyJsonWithRateLimitOverride(fieldName, 7),
                7
        );

        when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());

        var updated = service.update(tenantId, command, actorId, true, true, false);

        assertThat(updated.maxMessagesPerPatientPerDay()).isEqualTo(7);
        assertThat(updated.notificationPolicyJson()).contains("\"" + fieldName + "\":7");
    }

    @ParameterizedTest(name = "{1} rejects zero")
    @MethodSource("rateLimitFields")
    void rejectsZeroForEachRateLimitField(String fieldName, String label) {
        NotificationSettingsUpdateCommand command = command(
                true, true, false, true,
                NotificationChannelPreference.EMAIL,
                NotificationChannelPreference.IN_APP,
                false,
                null,
                null,
                null,
                policyJsonWithRateLimitOverride(fieldName, 0)
        );

        assertThatThrownBy(() -> service.update(tenantId, command, actorId, true, true, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(label + " must be a whole number greater than zero.");
    }

    @ParameterizedTest(name = "{1} rejects negative values")
    @MethodSource("rateLimitFields")
    void rejectsNegativeForEachRateLimitField(String fieldName, String label) {
        NotificationSettingsUpdateCommand command = command(
                true, true, false, true,
                NotificationChannelPreference.EMAIL,
                NotificationChannelPreference.IN_APP,
                false,
                null,
                null,
                null,
                policyJsonWithRateLimitOverride(fieldName, -1)
        );

        assertThatThrownBy(() -> service.update(tenantId, command, actorId, true, true, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(label + " value cannot be negative.");
    }

    @ParameterizedTest(name = "{1} rejects decimals")
    @MethodSource("rateLimitFields")
    void rejectsDecimalForEachRateLimitField(String fieldName, String label) {
        NotificationSettingsUpdateCommand command = command(
                true, true, false, true,
                NotificationChannelPreference.EMAIL,
                NotificationChannelPreference.IN_APP,
                false,
                null,
                null,
                null,
                policyJsonWithRateLimitOverride(fieldName, 1.5d)
        );

        assertThatThrownBy(() -> service.update(tenantId, command, actorId, true, true, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(label + " must be a whole number.");
    }

    @ParameterizedTest(name = "{1} rejects malformed values")
    @MethodSource("rateLimitFields")
    void rejectsMalformedForEachRateLimitField(String fieldName, String label) {
        NotificationSettingsUpdateCommand command = command(
                true, true, false, true,
                NotificationChannelPreference.EMAIL,
                NotificationChannelPreference.IN_APP,
                false,
                null,
                null,
                null,
                policyJsonWithRateLimitOverride(fieldName, "abc")
        );

        assertThatThrownBy(() -> service.update(tenantId, command, actorId, true, true, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(label + " must be a whole number.");
    }

    @ParameterizedTest(name = "{1} rejects overflow values")
    @MethodSource("rateLimitFields")
    void rejectsOverflowForEachRateLimitField(String fieldName, String label) {
        NotificationSettingsUpdateCommand command = command(
                true, true, false, true,
                NotificationChannelPreference.EMAIL,
                NotificationChannelPreference.IN_APP,
                false,
                null,
                null,
                null,
                policyJsonWithRateLimitOverride(fieldName, "2147483648")
        );

        assertThatThrownBy(() -> service.update(tenantId, command, actorId, true, true, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(label + " value is too large.");
    }

    @Test
    void validRateLimitSaveRoundTripsThroughReadModel() {
        final TenantNotificationSettingsEntity[] state = new TenantNotificationSettingsEntity[1];
        when(repository.findByTenantId(tenantId)).thenAnswer(invocation -> Optional.ofNullable(state[0]));
        when(repository.save(any())).thenAnswer(invocation -> {
            state[0] = invocation.getArgument(0);
            return state[0];
        });

        String policyJson = policyJsonWithRateLimitOverrides(Map.of(
                "overallMessagesPerDay", 25,
                "marketingPerDay", 5,
                "reminderPerDay", 10,
                "maximumPerHour", 3,
                "perPatientPerDay", 2
        ));
        NotificationSettingsUpdateCommand command = command(
                true, true, false, true,
                NotificationChannelPreference.EMAIL,
                NotificationChannelPreference.IN_APP,
                false,
                null,
                null,
                null,
                policyJson,
                2
        );

        var updated = service.update(tenantId, command, actorId, true, true, false);
        var reloaded = service.findByTenantId(tenantId).orElseThrow();

        assertThat(updated.notificationPolicyJson()).isEqualTo(policyJson);
        assertThat(reloaded.notificationPolicyJson()).isEqualTo(policyJson);
        assertThat(reloaded.maxMessagesPerPatientPerDay()).isEqualTo(2);
    }

    @Test
    void invalidRateLimitSaveDoesNotPartiallyPersist() {
        TenantNotificationSettingsEntity existing = TenantNotificationSettingsEntity.createDefault(tenantId, actorId);
        existing.updateFrom(
                true, false, false, true,
                true, true, true, true, true, true, true, true, true, true,
                false, null, null, "UTC",
                NotificationChannelPreference.EMAIL, NotificationChannelPreference.IN_APP,
                false, true, true, 5,
                policyJsonWithRateLimitOverride("overallMessagesPerDay", 10),
                actorId
        );
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(existing));

        String originalPolicy = existing.getNotificationPolicyJson();
        assertThatThrownBy(() -> service.update(
                tenantId,
                command(
                        true, true, false, true,
                        NotificationChannelPreference.EMAIL,
                        NotificationChannelPreference.IN_APP,
                        false,
                        null,
                        null,
                        null,
                        policyJsonWithRateLimitOverride("overallMessagesPerDay", -1),
                        5
                ),
                actorId,
                true,
                true,
                false
        )).isInstanceOf(IllegalArgumentException.class);

        assertThat(existing.getNotificationPolicyJson()).isEqualTo(originalPolicy);
    }

    @Test
    void saveFailureLeavesPersistedStateUnchanged() {
        TenantNotificationSettingsEntity persisted = TenantNotificationSettingsEntity.createDefault(tenantId, actorId);
        persisted.updateFrom(
                true, false, false, true,
                true, true, true, true, true, true, true, true, true, true,
                false, null, null, "UTC",
                NotificationChannelPreference.EMAIL, NotificationChannelPreference.IN_APP,
                false, true, true, 5,
                policyJsonWithRateLimitOverride("overallMessagesPerDay", 10),
                actorId
        );
        String originalPolicy = persisted.getNotificationPolicyJson();
        int originalLimit = persisted.getMaxMessagesPerPatientPerDay();

        when(repository.findByTenantId(tenantId)).thenAnswer(invocation -> Optional.of(copyOf(persisted)));
        when(repository.save(any())).thenThrow(new RuntimeException("database unavailable"));

        assertThatThrownBy(() -> service.update(
                tenantId,
                command(
                        true, true, false, true,
                        NotificationChannelPreference.EMAIL,
                        NotificationChannelPreference.IN_APP,
                        false,
                        null,
                        null,
                        null,
                        policyJsonWithRateLimitOverride("overallMessagesPerDay", 12),
                        6
                ),
                actorId,
                true,
                true,
                false
        )).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("database unavailable");

        assertThat(persisted.getNotificationPolicyJson()).isEqualTo(originalPolicy);
        assertThat(persisted.getMaxMessagesPerPatientPerDay()).isEqualTo(originalLimit);
    }

    private NotificationSettingsUpdateCommand command(
            boolean emailEnabled,
            boolean smsEnabled,
            boolean whatsappEnabled,
            boolean inAppEnabled,
            NotificationChannelPreference defaultChannel,
            NotificationChannelPreference fallbackChannel,
            boolean quietHoursEnabled,
            String timezone,
            LocalTime quietHoursStart,
            LocalTime quietHoursEnd,
            String notificationPolicyJson
    ) {
        return command(
                emailEnabled,
                smsEnabled,
                whatsappEnabled,
                inAppEnabled,
                defaultChannel,
                fallbackChannel,
                quietHoursEnabled,
                timezone,
                quietHoursStart,
                quietHoursEnd,
                notificationPolicyJson,
                5
        );
    }

    private NotificationSettingsUpdateCommand command(
            boolean emailEnabled,
            boolean smsEnabled,
            boolean whatsappEnabled,
            boolean inAppEnabled,
            NotificationChannelPreference defaultChannel,
            NotificationChannelPreference fallbackChannel,
            boolean quietHoursEnabled,
            String timezone,
            LocalTime quietHoursStart,
            LocalTime quietHoursEnd,
            String notificationPolicyJson,
            int maxMessagesPerPatientPerDay
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
                quietHoursStart,
                quietHoursEnd,
                timezone,
                defaultChannel,
                fallbackChannel,
                false,
                true,
                true,
                maxMessagesPerPatientPerDay,
                notificationPolicyJson
        );
    }

    private String quietHoursPolicyJson(List<String> weekdays, String effectiveFrom, String effectiveUntil) {
        StringBuilder json = new StringBuilder("{\"quietHoursSchedule\":{");
        json.append("\"weekdays\":[");
        for (int i = 0; i < weekdays.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append('\"').append(weekdays.get(i)).append('\"');
        }
        json.append(']');
        if (effectiveFrom != null) {
            json.append(",\"effectiveFrom\":\"").append(effectiveFrom).append('\"');
        }
        if (effectiveUntil != null) {
            json.append(",\"effectiveUntil\":\"").append(effectiveUntil).append('\"');
        }
        json.append("}}");
        return json.toString();
    }

    private String policyJsonWithRateLimitOverride(String fieldName, Object value) {
        return policyJsonWithRateLimitOverrides(Map.of(fieldName, value));
    }

    private String policyJsonWithRateLimitOverrides(Map<String, Object> overrides) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode rateLimits = root.putObject("rateLimits");
        rateLimits.put("overallMessagesPerDay", 100);
        rateLimits.put("marketingPerDay", 20);
        rateLimits.put("reminderPerDay", 40);
        rateLimits.put("maximumPerHour", 12);
        rateLimits.put("perPatientPerDay", 5);
        overrides.forEach((fieldName, value) -> {
            if (value == null) {
                rateLimits.putNull(fieldName);
            } else if (value instanceof Integer integer) {
                rateLimits.put(fieldName, integer);
            } else if (value instanceof Long longValue) {
                rateLimits.put(fieldName, longValue);
            } else if (value instanceof Double doubleValue) {
                rateLimits.put(fieldName, doubleValue);
            } else if (value instanceof Float floatValue) {
                rateLimits.put(fieldName, floatValue);
            } else if (value instanceof Boolean booleanValue) {
                rateLimits.put(fieldName, booleanValue);
            } else {
                rateLimits.put(fieldName, value.toString());
            }
        });
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build test policy JSON", ex);
        }
    }

    private TenantNotificationSettingsEntity copyOf(TenantNotificationSettingsEntity source) {
        TenantNotificationSettingsEntity copy = TenantNotificationSettingsEntity.createDefault(source.getTenantId(), source.getCreatedBy());
        copy.updateFrom(
                source.isEmailEnabled(),
                source.isSmsEnabled(),
                source.isWhatsappEnabled(),
                source.isInAppEnabled(),
                source.isAppointmentRemindersEnabled(),
                source.isAppointmentReminder24hEnabled(),
                source.isAppointmentReminder2hEnabled(),
                source.isFollowUpRemindersEnabled(),
                source.isBillingRemindersEnabled(),
                source.isRefillRemindersEnabled(),
                source.isVaccinationRemindersEnabled(),
                source.isLeadFollowUpRemindersEnabled(),
                source.isWebinarRemindersEnabled(),
                source.isBirthdayWellnessEnabled(),
                source.isQuietHoursEnabled(),
                source.getQuietHoursStart(),
                source.getQuietHoursEnd(),
                source.getTimezone(),
                source.getDefaultChannel(),
                source.getFallbackChannel(),
                source.isAllowMarketingMessages(),
                source.isRequirePatientConsent(),
                source.isUnsubscribeFooterEnabled(),
                source.getMaxMessagesPerPatientPerDay(),
                source.getNotificationPolicyJson(),
                source.getUpdatedBy()
        );
        return copy;
    }
}
