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
            boolean voiceEnabled,
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
            String clinicTimeZone,
            OffsetDateTime clinicNow,
            OffsetDateTime serverNowUtc,
            NotificationChannelPreference defaultChannel,
            NotificationChannelPreference fallbackChannel,
            boolean allowMarketingMessages,
            boolean requirePatientConsent,
            boolean unsubscribeFooterEnabled,
            int maxMessagesPerPatientPerDay,
            String notificationPolicyJson,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            UUID createdBy,
            UUID updatedBy,
            boolean emailReady,
            boolean smsReady,
            boolean whatsappReady,
            boolean voiceReady,
            String voiceProvider,
            boolean voiceExecutionEnabled,
            List<String> warnings
    ) {
    }

    public record UpdateNotificationSettingsRequest(
            boolean emailEnabled,
            boolean smsEnabled,
            boolean whatsappEnabled,
            boolean voiceEnabled,
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
            String notificationPolicyJson
    ) {
        public UpdateNotificationSettingsRequest(
                boolean emailEnabled, boolean smsEnabled, boolean whatsappEnabled, boolean inAppEnabled,
                boolean appointmentRemindersEnabled, boolean appointmentReminder24hEnabled,
                boolean appointmentReminder2hEnabled, boolean followUpRemindersEnabled,
                boolean billingRemindersEnabled, boolean refillRemindersEnabled,
                boolean vaccinationRemindersEnabled, boolean leadFollowUpRemindersEnabled,
                boolean webinarRemindersEnabled, boolean birthdayWellnessEnabled, boolean quietHoursEnabled,
                LocalTime quietHoursStart, LocalTime quietHoursEnd, String timezone,
                NotificationChannelPreference defaultChannel, NotificationChannelPreference fallbackChannel,
                boolean allowMarketingMessages, boolean requirePatientConsent, boolean unsubscribeFooterEnabled,
                int maxMessagesPerPatientPerDay, String notificationPolicyJson
        ) {
            this(emailEnabled, smsEnabled, whatsappEnabled, false, inAppEnabled, appointmentRemindersEnabled,
                    appointmentReminder24hEnabled, appointmentReminder2hEnabled, followUpRemindersEnabled,
                    billingRemindersEnabled, refillRemindersEnabled, vaccinationRemindersEnabled,
                    leadFollowUpRemindersEnabled, webinarRemindersEnabled, birthdayWellnessEnabled,
                    quietHoursEnabled, quietHoursStart, quietHoursEnd, timezone, defaultChannel, fallbackChannel,
                    allowMarketingMessages, requirePatientConsent, unsubscribeFooterEnabled,
                    maxMessagesPerPatientPerDay, notificationPolicyJson);
        }
    }
}
