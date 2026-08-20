package com.deepthoughtnet.clinic.carepilot.notificationsettings.service;

import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.db.TenantNotificationSettingsEntity;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.db.TenantNotificationSettingsRepository;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.model.NotificationChannelPreference;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.service.model.NotificationSettingsRecord;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.service.model.NotificationSettingsUpdateCommand;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.deepthoughtnet.clinic.carepilot.shared.util.CarePilotValidators;
import java.math.BigInteger;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Tenant-level operational notification settings service for Clinic + CarePilot modules.
 */
@Service
public class TenantNotificationSettingsService {
    private static final List<DayOfWeek> DEFAULT_WEEKDAYS = List.of(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY,
            DayOfWeek.SUNDAY
    );

    private final TenantNotificationSettingsRepository repository;
    private final ObjectMapper objectMapper;

    public TenantNotificationSettingsService(TenantNotificationSettingsRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns existing settings or lazily creates tenant defaults.
     */
    @Transactional
    public NotificationSettingsRecord getOrCreate(UUID tenantId) {
        CarePilotValidators.requireTenant(tenantId);
        TenantNotificationSettingsEntity row = repository.findByTenantId(tenantId)
                .orElseGet(() -> repository.save(TenantNotificationSettingsEntity.createDefault(tenantId, null)));
        return toRecord(row);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<NotificationSettingsRecord> findByTenantId(UUID tenantId) {
        CarePilotValidators.requireTenant(tenantId);
        return repository.findByTenantId(tenantId).map(this::toRecord);
    }

    /**
     * Updates settings after validating cross-field channel/quiet-hour constraints.
     */
    @Transactional
    public NotificationSettingsRecord update(UUID tenantId, NotificationSettingsUpdateCommand command, UUID actorId) {
        return update(tenantId, command, actorId, true, true, true);
    }

    @Transactional
    public NotificationSettingsRecord update(
            UUID tenantId,
            NotificationSettingsUpdateCommand command,
            UUID actorId,
            boolean emailReady,
            boolean smsReady,
            boolean whatsappReady
    ) {
        CarePilotValidators.requireTenant(tenantId);
        if (command == null) {
            throw new IllegalArgumentException("Notification settings payload is required");
        }
        validate(command, emailReady, smsReady, whatsappReady);

        TenantNotificationSettingsEntity row = repository.findByTenantId(tenantId)
                .orElseGet(() -> TenantNotificationSettingsEntity.createDefault(tenantId, actorId));

        row.updateFrom(
                command.emailEnabled(),
                command.smsEnabled(),
                command.whatsappEnabled(),
                command.inAppEnabled(),
                command.appointmentRemindersEnabled(),
                command.appointmentReminder24hEnabled(),
                command.appointmentReminder2hEnabled(),
                command.followUpRemindersEnabled(),
                command.billingRemindersEnabled(),
                command.refillRemindersEnabled(),
                command.vaccinationRemindersEnabled(),
                command.leadFollowUpRemindersEnabled(),
                command.webinarRemindersEnabled(),
                command.birthdayWellnessEnabled(),
                command.quietHoursEnabled(),
                command.quietHoursStart(),
                command.quietHoursEnd(),
                normalizeTimezone(command.timezone()),
                command.defaultChannel(),
                command.fallbackChannel(),
                command.allowMarketingMessages(),
                command.requirePatientConsent(),
                command.unsubscribeFooterEnabled(),
                command.maxMessagesPerPatientPerDay(),
                command.notificationPolicyJson() != null ? command.notificationPolicyJson() : row.getNotificationPolicyJson(),
                actorId
        );
        return toRecord(repository.save(row));
    }

    public List<String> computeWarnings(NotificationSettingsRecord settings, boolean emailReady, boolean smsReady, boolean whatsappReady) {
        List<String> warnings = new ArrayList<>();
        if (settings.smsEnabled() && !smsReady) {
            warnings.add("SMS enabled but provider not configured");
        }
        if (settings.whatsappEnabled() && !whatsappReady) {
            warnings.add("WhatsApp enabled but provider not configured");
        }
        if (settings.emailEnabled() && !emailReady) {
            warnings.add("Email enabled but provider not configured");
        }
        if (settings.quietHoursEnabled() && !StringUtils.hasText(settings.timezone())) {
            warnings.add("Quiet hours timezone missing");
        }
        if (settings.defaultChannel() != null && !isChannelUsable(settings, toChannelType(settings.defaultChannel()), emailReady, smsReady, whatsappReady)) {
            warnings.add("Current default channel is unavailable. Select a ready and enabled channel.");
        }
        if (settings.fallbackChannel() != null && !isChannelUsable(settings, toChannelType(settings.fallbackChannel()), emailReady, smsReady, whatsappReady)) {
            warnings.add("Current fallback channel is unavailable. Select a configured and enabled channel, or None.");
        }
        return List.copyOf(warnings);
    }

    /**
     * Resolves effective channel with fallback when requested channel is disabled by tenant settings.
     */
    public ChannelType resolveEffectiveChannel(NotificationSettingsRecord settings, ChannelType requestedChannel) {
        return resolveEffectiveChannel(settings, requestedChannel, true, true, true);
    }

    public ChannelType resolveEffectiveChannel(
            NotificationSettingsRecord settings,
            ChannelType requestedChannel,
            boolean emailReady,
            boolean smsReady,
            boolean whatsappReady
    ) {
        if (settings == null || requestedChannel == null) {
            return requestedChannel;
        }
        if (isChannelUsable(settings, requestedChannel, emailReady, smsReady, whatsappReady)) {
            return requestedChannel;
        }
        ChannelType fallback = toChannelType(settings.fallbackChannel());
        if (fallback != null && isChannelUsable(settings, fallback, emailReady, smsReady, whatsappReady)) {
            return fallback;
        }
        ChannelType preferred = toChannelType(settings.defaultChannel());
        if (preferred != null && isChannelUsable(settings, preferred, emailReady, smsReady, whatsappReady)) {
            return preferred;
        }
        return null;
    }

    /**
     * Applies quiet-hour deferral when enabled, returning original timestamp otherwise.
     */
    public OffsetDateTime applyQuietHours(NotificationSettingsRecord settings, OffsetDateTime scheduledAt) {
        return applyQuietHours(settings, scheduledAt, false);
    }

    public OffsetDateTime applyQuietHours(NotificationSettingsRecord settings, OffsetDateTime scheduledAt, boolean bypassQuietHours) {
        if (settings == null || scheduledAt == null || bypassQuietHours || !settings.quietHoursEnabled()) {
            return scheduledAt;
        }
        if (!StringUtils.hasText(settings.timezone()) || settings.quietHoursStart() == null || settings.quietHoursEnd() == null) {
            return scheduledAt;
        }
        ZoneId zone;
        try {
            zone = ZoneId.of(settings.timezone().trim());
        } catch (Exception ex) {
            return scheduledAt;
        }

        LocalDateTime local = scheduledAt.atZoneSameInstant(zone).toLocalDateTime();
        QuietHoursSchedule schedule = parseQuietHoursSchedule(settings.notificationPolicyJson());
        LocalDate localDate = local.toLocalDate();
        if (!isQuietHoursActiveOn(localDate, schedule)) {
            return scheduledAt;
        }
        LocalTime t = local.toLocalTime();
        LocalTime start = settings.quietHoursStart();
        LocalTime end = settings.quietHoursEnd();

        if (!isInQuietHours(t, start, end)) {
            return scheduledAt;
        }

        LocalDateTime adjusted;
        if (start.equals(end)) {
            adjusted = local.plusHours(1);
        } else if (start.isBefore(end)) {
            adjusted = LocalDateTime.of(local.toLocalDate(), end);
        } else {
            adjusted = t.isBefore(end)
                    ? LocalDateTime.of(local.toLocalDate(), end)
                    : LocalDateTime.of(local.toLocalDate().plusDays(1), end);
        }
        return adjusted.atZone(zone).toOffsetDateTime();
    }

    public boolean isChannelEnabled(NotificationSettingsRecord settings, ChannelType channelType) {
        if (settings == null || channelType == null) {
            return false;
        }
        return switch (channelType) {
            case EMAIL -> settings.emailEnabled();
            case SMS -> settings.smsEnabled();
            case WHATSAPP -> settings.whatsappEnabled();
            case IN_APP, APP_NOTIFICATION -> settings.inAppEnabled();
        };
    }

    private boolean isInQuietHours(LocalTime value, LocalTime start, LocalTime end) {
        if (start.equals(end)) {
            return true;
        }
        if (start.isBefore(end)) {
            return !value.isBefore(start) && value.isBefore(end);
        }
        return !value.isBefore(start) || value.isBefore(end);
    }

    private void validate(NotificationSettingsUpdateCommand command) {
        validate(command, true, true, true);
    }

    private void validate(NotificationSettingsUpdateCommand command, boolean emailReady, boolean smsReady, boolean whatsappReady) {
        if (command.defaultChannel() == null) {
            throw new IllegalArgumentException("Select a default channel.");
        }
        if (!isPreferenceUsable(command, command.defaultChannel(), emailReady, smsReady, whatsappReady)) {
            throw new IllegalArgumentException("Default channel must be enabled and ready for use.");
        }
        if (command.fallbackChannel() != null && command.fallbackChannel() == command.defaultChannel()) {
            throw new IllegalArgumentException("Fallback channel must be different from the default channel.");
        }
        if (command.fallbackChannel() != null && !isPreferenceUsable(command, command.fallbackChannel(), emailReady, smsReady, whatsappReady)) {
            throw new IllegalArgumentException("Fallback channel must be enabled and ready for use.");
        }
        if (StringUtils.hasText(command.timezone())) {
            try {
                ZoneId.of(command.timezone().trim());
            } catch (Exception ex) {
                throw new IllegalArgumentException("Select a valid timezone.");
            }
        }
        if (command.quietHoursEnabled()) {
            if (!StringUtils.hasText(command.timezone())) {
                throw new IllegalArgumentException("Timezone is required when quiet hours are enabled.");
            }
            if (command.quietHoursStart() == null) {
                throw new IllegalArgumentException("Start time is required when quiet hours are enabled.");
            }
            if (command.quietHoursEnd() == null) {
                throw new IllegalArgumentException("End time is required when quiet hours are enabled.");
            }
            QuietHoursSchedule schedule = parseQuietHoursSchedule(command.notificationPolicyJson());
            if (schedule.weekdays().isEmpty()) {
                throw new IllegalArgumentException("Select at least one weekday when quiet hours are enabled.");
            }
            if (hasInvalidQuietHoursDate(command.notificationPolicyJson(), "effectiveFrom")
                    || hasInvalidQuietHoursDate(command.notificationPolicyJson(), "effectiveUntil")) {
                throw new IllegalArgumentException("Quiet hours effective dates must be valid dates.");
            }
            if (schedule.effectiveFrom() != null && schedule.effectiveUntil() != null && schedule.effectiveFrom().isAfter(schedule.effectiveUntil())) {
                throw new IllegalArgumentException("Effective From must be on or before Effective Until.");
            }
        }
        validateRateLimits(command.notificationPolicyJson());
        if (command.maxMessagesPerPatientPerDay() <= 0) {
            throw new IllegalArgumentException("Per patient/day limit must be greater than 0.");
        }
    }

    private String normalizeTimezone(String timezone) {
        if (!StringUtils.hasText(timezone)) {
            return "Asia/Kolkata";
        }
        return timezone.trim();
    }

    private boolean isPreferenceEnabled(NotificationSettingsUpdateCommand command, NotificationChannelPreference pref) {
        return switch (pref) {
            case EMAIL -> command.emailEnabled();
            case SMS -> command.smsEnabled();
            case WHATSAPP -> command.whatsappEnabled();
            case IN_APP -> command.inAppEnabled();
        };
    }

    private boolean isPreferenceUsable(
            NotificationSettingsUpdateCommand command,
            NotificationChannelPreference pref,
            boolean emailReady,
            boolean smsReady,
            boolean whatsappReady
    ) {
        return isPreferenceEnabled(command, pref) && isPreferenceReady(pref, emailReady, smsReady, whatsappReady);
    }

    private boolean isPreferenceReady(NotificationChannelPreference pref, boolean emailReady, boolean smsReady, boolean whatsappReady) {
        return switch (pref) {
            case EMAIL -> emailReady;
            case SMS -> smsReady;
            case WHATSAPP -> whatsappReady;
            case IN_APP -> true;
        };
    }

    private boolean isChannelUsable(
            NotificationSettingsRecord settings,
            ChannelType channelType,
            boolean emailReady,
            boolean smsReady,
            boolean whatsappReady
    ) {
        if (settings == null || channelType == null) {
            return false;
        }
        return switch (channelType) {
            case EMAIL -> settings.emailEnabled() && emailReady;
            case SMS -> settings.smsEnabled() && smsReady;
            case WHATSAPP -> settings.whatsappEnabled() && whatsappReady;
            case IN_APP, APP_NOTIFICATION -> settings.inAppEnabled();
        };
    }

    private boolean isQuietHoursActiveOn(LocalDate date, QuietHoursSchedule schedule) {
        if (date == null || schedule == null) {
            return true;
        }
        if (!schedule.weekdays().isEmpty() && !schedule.weekdays().contains(date.getDayOfWeek())) {
            return false;
        }
        if (schedule.effectiveFrom() != null && date.isBefore(schedule.effectiveFrom())) {
            return false;
        }
        return schedule.effectiveUntil() == null || !date.isAfter(schedule.effectiveUntil());
    }

    private QuietHoursSchedule parseQuietHoursSchedule(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return new QuietHoursSchedule(DEFAULT_WEEKDAYS, null, null);
        }
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            if (root == null || root.isNull()) {
                return new QuietHoursSchedule(DEFAULT_WEEKDAYS, null, null);
            }
            JsonNode scheduleNode = root.path("quietHoursSchedule");
            if (scheduleNode.isMissingNode() || scheduleNode.isNull() || !scheduleNode.isObject()) {
                return new QuietHoursSchedule(DEFAULT_WEEKDAYS, null, null);
            }

            List<DayOfWeek> weekdays = parseQuietHoursWeekdays(scheduleNode.path("weekdays"));
            LocalDate effectiveFrom = parseQuietHoursDate(scheduleNode, "effectiveFrom");
            LocalDate effectiveUntil = parseQuietHoursDate(scheduleNode, "effectiveUntil");
            return new QuietHoursSchedule(weekdays, effectiveFrom, effectiveUntil);
        } catch (Exception ex) {
            return new QuietHoursSchedule(DEFAULT_WEEKDAYS, null, null);
        }
    }

    private void validateRateLimits(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return;
        }
        final JsonNode root;
        try {
            root = objectMapper.readTree(rawJson);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Notification policy must be valid JSON.");
        }
        if (root == null || root.isNull()) {
            return;
        }
        JsonNode rateLimits = root.path("rateLimits");
        if (rateLimits.isMissingNode() || rateLimits.isNull()) {
            return;
        }
        if (!rateLimits.isObject()) {
            throw new IllegalArgumentException("Rate limits must be an object.");
        }
        validateRateLimit(rateLimits, "overallMessagesPerDay", "Overall messages/day");
        validateRateLimit(rateLimits, "marketingPerDay", "Marketing/day");
        validateRateLimit(rateLimits, "reminderPerDay", "Reminder/day");
        validateRateLimit(rateLimits, "maximumPerHour", "Maximum/hour");
        validateRateLimit(rateLimits, "perPatientPerDay", "Per patient/day");
    }

    private void validateRateLimit(JsonNode rateLimits, String fieldName, String label) {
        JsonNode field = rateLimits.get(fieldName);
        if (field == null || field.isMissingNode() || field.isNull()) {
            throw new IllegalArgumentException(label + " must be a whole number greater than zero.");
        }
        String rawValue;
        if (field.isTextual()) {
            rawValue = field.asText().trim();
            if (!StringUtils.hasText(rawValue)) {
                throw new IllegalArgumentException(label + " must be a whole number greater than zero.");
            }
        } else if (field.isNumber()) {
            if (!field.isIntegralNumber()) {
                throw new IllegalArgumentException(label + " must be a whole number.");
            }
            rawValue = field.asText().trim();
        } else {
            throw new IllegalArgumentException(label + " must be a whole number.");
        }
        final BigInteger parsed;
        try {
            parsed = new BigInteger(rawValue);
        } catch (Exception ex) {
            throw new IllegalArgumentException(label + " must be a whole number.");
        }
        if (parsed.signum() < 0) {
            throw new IllegalArgumentException(label + " value cannot be negative.");
        }
        if (parsed.signum() == 0) {
            throw new IllegalArgumentException(label + " must be a whole number greater than zero.");
        }
        if (parsed.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
            throw new IllegalArgumentException(label + " value is too large.");
        }
    }

    private List<DayOfWeek> parseQuietHoursWeekdays(JsonNode weekdaysNode) {
        if (weekdaysNode == null || weekdaysNode.isMissingNode() || weekdaysNode.isNull()) {
            return DEFAULT_WEEKDAYS;
        }
        if (!weekdaysNode.isArray()) {
            return List.of();
        }
        EnumSet<DayOfWeek> weekdays = EnumSet.noneOf(DayOfWeek.class);
        for (JsonNode node : weekdaysNode) {
            if (node == null || !node.isTextual()) {
                continue;
            }
            String value = node.asText().trim();
            if (!StringUtils.hasText(value)) {
                continue;
            }
            try {
                weekdays.add(DayOfWeek.valueOf(value.toUpperCase(Locale.ROOT)));
            } catch (Exception ignored) {
                // validation handles empty/invalid schedules when quiet hours are enabled
            }
        }
        return List.copyOf(weekdays);
    }

    private LocalDate parseQuietHoursDate(JsonNode scheduleNode, String fieldName) {
        if (scheduleNode == null || scheduleNode.isMissingNode() || scheduleNode.isNull()) {
            return null;
        }
        JsonNode field = scheduleNode.path(fieldName);
        if (field.isMissingNode() || field.isNull() || !field.isTextual() || !StringUtils.hasText(field.asText())) {
            return null;
        }
        try {
            return LocalDate.parse(field.asText().trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean hasInvalidQuietHoursDate(String rawJson, String fieldName) {
        if (!StringUtils.hasText(rawJson)) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            if (root == null || root.isNull()) {
                return false;
            }
            JsonNode scheduleNode = root.path("quietHoursSchedule");
            if (scheduleNode.isMissingNode() || scheduleNode.isNull() || !scheduleNode.isObject()) {
                return false;
            }
            JsonNode field = scheduleNode.path(fieldName);
            if (field.isMissingNode() || field.isNull() || !field.isTextual() || !StringUtils.hasText(field.asText())) {
                return false;
            }
            LocalDate.parse(field.asText().trim());
            return false;
        } catch (Exception ex) {
            return true;
        }
    }

    private record QuietHoursSchedule(List<DayOfWeek> weekdays, LocalDate effectiveFrom, LocalDate effectiveUntil) {
    }

    private ChannelType toChannelType(NotificationChannelPreference preference) {
        if (preference == null) {
            return null;
        }
        return switch (preference) {
            case EMAIL -> ChannelType.EMAIL;
            case SMS -> ChannelType.SMS;
            case WHATSAPP -> ChannelType.WHATSAPP;
            case IN_APP -> ChannelType.IN_APP;
        };
    }

    private NotificationSettingsRecord toRecord(TenantNotificationSettingsEntity row) {
        return new NotificationSettingsRecord(
                row.getId(),
                row.getTenantId(),
                row.isEmailEnabled(),
                row.isSmsEnabled(),
                row.isWhatsappEnabled(),
                row.isInAppEnabled(),
                row.isAppointmentRemindersEnabled(),
                row.isAppointmentReminder24hEnabled(),
                row.isAppointmentReminder2hEnabled(),
                row.isFollowUpRemindersEnabled(),
                row.isBillingRemindersEnabled(),
                row.isRefillRemindersEnabled(),
                row.isVaccinationRemindersEnabled(),
                row.isLeadFollowUpRemindersEnabled(),
                row.isWebinarRemindersEnabled(),
                row.isBirthdayWellnessEnabled(),
                row.isQuietHoursEnabled(),
                row.getQuietHoursStart(),
                row.getQuietHoursEnd(),
                row.getTimezone(),
                row.getDefaultChannel(),
                row.getFallbackChannel(),
                row.isAllowMarketingMessages(),
                row.isRequirePatientConsent(),
                row.isUnsubscribeFooterEnabled(),
                row.getMaxMessagesPerPatientPerDay(),
                row.getNotificationPolicyJson(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getCreatedBy(),
                row.getUpdatedBy()
        );
    }

    NotificationSettingsRecord toRecordForTest(TenantNotificationSettingsEntity row) {
        return toRecord(row);
    }
}
