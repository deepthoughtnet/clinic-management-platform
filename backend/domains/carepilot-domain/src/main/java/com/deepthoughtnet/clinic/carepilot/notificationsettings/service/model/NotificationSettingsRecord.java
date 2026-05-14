package com.deepthoughtnet.clinic.carepilot.notificationsettings.service.model;

import com.deepthoughtnet.clinic.carepilot.notificationsettings.model.NotificationChannelPreference;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Tenant notification settings read model.
 */
public record NotificationSettingsRecord(
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
        UUID updatedBy
) {
}
