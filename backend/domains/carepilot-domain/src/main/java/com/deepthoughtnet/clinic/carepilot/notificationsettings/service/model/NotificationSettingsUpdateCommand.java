package com.deepthoughtnet.clinic.carepilot.notificationsettings.service.model;

import com.deepthoughtnet.clinic.carepilot.notificationsettings.model.NotificationChannelPreference;
import java.time.LocalTime;

/**
 * Mutable fields for tenant notification settings update.
 */
public record NotificationSettingsUpdateCommand(
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
    /** Compatibility constructor for callers that predate tenant Voice settings. */
    public NotificationSettingsUpdateCommand(
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
