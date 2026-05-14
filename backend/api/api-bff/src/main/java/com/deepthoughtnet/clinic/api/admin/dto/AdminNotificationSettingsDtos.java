package com.deepthoughtnet.clinic.api.admin.dto;

import com.deepthoughtnet.clinic.carepilot.notificationsettings.model.NotificationChannelPreference;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTOs for administration notification settings APIs.
 */
public final class AdminNotificationSettingsDtos {
    private AdminNotificationSettingsDtos() {
    }

    public record NotificationSettingsResponse(
            UUID id,
            UUID tenantId,
            boolean emailEnabled,
            boolean smsEnabled,
            boolean whatsappEnabled,
            boolean inAppEnabled,
            boolean appointmentRemindersEnabled,
            boolean appointmentReminder24hEnabled,
            boolean appointmentReminder2hEnabled,
            boolean followUpRemindersEnabled,
            boolean billingRemindersEnabled,
            boolean refillRemindersEnabled,
            boolean vaccinationRemindersEnabled,
            boolean leadFollowUpRemindersEnabled,
            boolean webinarRemindersEnabled,
            boolean birthdayWellnessEnabled,
            boolean quietHoursEnabled,
            LocalTime quietHoursStart,
            LocalTime quietHoursEnd,
            String timezone,
            NotificationChannelPreference defaultChannel,
            NotificationChannelPreference fallbackChannel,
            boolean allowMarketingMessages,
            boolean requirePatientConsent,
            boolean unsubscribeFooterEnabled,
            int maxMessagesPerPatientPerDay,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            UUID createdBy,
            UUID updatedBy,
            boolean emailReady,
            boolean smsReady,
            boolean whatsappReady,
            List<String> warnings
    ) {
    }

    public record UpdateNotificationSettingsRequest(
            boolean emailEnabled,
            boolean smsEnabled,
            boolean whatsappEnabled,
            boolean inAppEnabled,
            boolean appointmentRemindersEnabled,
            boolean appointmentReminder24hEnabled,
            boolean appointmentReminder2hEnabled,
            boolean followUpRemindersEnabled,
            boolean billingRemindersEnabled,
            boolean refillRemindersEnabled,
            boolean vaccinationRemindersEnabled,
            boolean leadFollowUpRemindersEnabled,
            boolean webinarRemindersEnabled,
            boolean birthdayWellnessEnabled,
            boolean quietHoursEnabled,
            LocalTime quietHoursStart,
            LocalTime quietHoursEnd,
            String timezone,
            NotificationChannelPreference defaultChannel,
            NotificationChannelPreference fallbackChannel,
            boolean allowMarketingMessages,
            boolean requirePatientConsent,
            boolean unsubscribeFooterEnabled,
            int maxMessagesPerPatientPerDay
    ) {
    }
}
