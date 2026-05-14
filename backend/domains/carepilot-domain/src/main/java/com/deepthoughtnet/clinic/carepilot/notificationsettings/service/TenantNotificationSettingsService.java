package com.deepthoughtnet.clinic.carepilot.notificationsettings.service;

import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.db.TenantNotificationSettingsEntity;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.db.TenantNotificationSettingsRepository;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.model.NotificationChannelPreference;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.service.model.NotificationSettingsRecord;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.service.model.NotificationSettingsUpdateCommand;
import com.deepthoughtnet.clinic.carepilot.shared.util.CarePilotValidators;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Tenant-level operational notification settings service for Clinic + CarePilot modules.
 */
@Service
public class TenantNotificationSettingsService {
    private final TenantNotificationSettingsRepository repository;

    public TenantNotificationSettingsService(TenantNotificationSettingsRepository repository) {
        this.repository = repository;
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

    /**
     * Updates settings after validating cross-field channel/quiet-hour constraints.
     */
    @Transactional
    public NotificationSettingsRecord update(UUID tenantId, NotificationSettingsUpdateCommand command, UUID actorId) {
        CarePilotValidators.requireTenant(tenantId);
        if (command == null) {
            throw new IllegalArgumentException("Notification settings payload is required");
        }
        validate(command);

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
                command.quietHoursEnabled() ? command.quietHoursStart() : null,
                command.quietHoursEnabled() ? command.quietHoursEnd() : null,
                normalizeTimezone(command.timezone()),
                command.defaultChannel(),
                command.fallbackChannel(),
                command.allowMarketingMessages(),
                command.requirePatientConsent(),
                command.unsubscribeFooterEnabled(),
                command.maxMessagesPerPatientPerDay(),
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
        return List.copyOf(warnings);
    }

    /**
     * Resolves effective channel with fallback when requested channel is disabled by tenant settings.
     */
    public ChannelType resolveEffectiveChannel(NotificationSettingsRecord settings, ChannelType requestedChannel) {
        if (settings == null || requestedChannel == null) {
            return requestedChannel;
        }
        if (isChannelEnabled(settings, requestedChannel)) {
            return requestedChannel;
        }
        ChannelType fallback = toChannelType(settings.fallbackChannel());
        if (fallback != null && isChannelEnabled(settings, fallback)) {
            return fallback;
        }
        ChannelType preferred = toChannelType(settings.defaultChannel());
        if (preferred != null && isChannelEnabled(settings, preferred)) {
            return preferred;
        }
        return null;
    }

    /**
     * Applies quiet-hour deferral when enabled, returning original timestamp otherwise.
     */
    public OffsetDateTime applyQuietHours(NotificationSettingsRecord settings, OffsetDateTime scheduledAt) {
        if (settings == null || scheduledAt == null || !settings.quietHoursEnabled()) {
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
        if (command.defaultChannel() == null) {
            throw new IllegalArgumentException("defaultChannel is required");
        }
        if (!isPreferenceEnabled(command, command.defaultChannel())) {
            throw new IllegalArgumentException("defaultChannel must be enabled");
        }
        if (command.fallbackChannel() != null && command.fallbackChannel() == command.defaultChannel()) {
            throw new IllegalArgumentException("fallbackChannel must be different from defaultChannel");
        }
        if (command.fallbackChannel() != null && !isPreferenceEnabled(command, command.fallbackChannel())) {
            throw new IllegalArgumentException("fallbackChannel must be enabled when provided");
        }
        if (command.quietHoursEnabled()) {
            if (!StringUtils.hasText(command.timezone())) {
                throw new IllegalArgumentException("timezone is required when quiet hours are enabled");
            }
            if (command.quietHoursStart() == null || command.quietHoursEnd() == null) {
                throw new IllegalArgumentException("quietHoursStart and quietHoursEnd are required when quiet hours are enabled");
            }
            try {
                ZoneId.of(command.timezone().trim());
            } catch (Exception ex) {
                throw new IllegalArgumentException("Invalid timezone");
            }
        }
        if (command.maxMessagesPerPatientPerDay() <= 0) {
            throw new IllegalArgumentException("maxMessagesPerPatientPerDay must be positive");
        }
    }

    private String normalizeTimezone(String timezone) {
        if (!StringUtils.hasText(timezone)) {
            return "UTC";
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
