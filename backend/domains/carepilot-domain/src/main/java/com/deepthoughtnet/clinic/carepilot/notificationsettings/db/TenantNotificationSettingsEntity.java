package com.deepthoughtnet.clinic.carepilot.notificationsettings.db;

import com.deepthoughtnet.clinic.carepilot.notificationsettings.model.NotificationChannelPreference;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Tenant-scoped communication defaults used by Clinic + CarePilot notification orchestration.
 */
@Entity
@Table(name = "tenant_notification_settings", indexes = {
        @Index(name = "ix_tns_tenant", columnList = "tenant_id")
})
public class TenantNotificationSettingsEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    @Column(name = "sms_enabled", nullable = false)
    private boolean smsEnabled;

    @Column(name = "whatsapp_enabled", nullable = false)
    private boolean whatsappEnabled;

    @Column(name = "in_app_enabled", nullable = false)
    private boolean inAppEnabled;

    @Column(name = "appointment_reminders_enabled", nullable = false)
    private boolean appointmentRemindersEnabled;

    @Column(name = "appointment_reminder_24h_enabled", nullable = false)
    private boolean appointmentReminder24hEnabled;

    @Column(name = "appointment_reminder_2h_enabled", nullable = false)
    private boolean appointmentReminder2hEnabled;

    @Column(name = "follow_up_reminders_enabled", nullable = false)
    private boolean followUpRemindersEnabled;

    @Column(name = "billing_reminders_enabled", nullable = false)
    private boolean billingRemindersEnabled;

    @Column(name = "refill_reminders_enabled", nullable = false)
    private boolean refillRemindersEnabled;

    @Column(name = "vaccination_reminders_enabled", nullable = false)
    private boolean vaccinationRemindersEnabled;

    @Column(name = "lead_follow_up_reminders_enabled", nullable = false)
    private boolean leadFollowUpRemindersEnabled;

    @Column(name = "webinar_reminders_enabled", nullable = false)
    private boolean webinarRemindersEnabled;

    @Column(name = "birthday_wellness_enabled", nullable = false)
    private boolean birthdayWellnessEnabled;

    @Column(name = "quiet_hours_enabled", nullable = false)
    private boolean quietHoursEnabled;

    @Column(name = "quiet_hours_start")
    private LocalTime quietHoursStart;

    @Column(name = "quiet_hours_end")
    private LocalTime quietHoursEnd;

    @Column(name = "timezone", length = 64)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_channel", nullable = false, length = 24)
    private NotificationChannelPreference defaultChannel;

    @Enumerated(EnumType.STRING)
    @Column(name = "fallback_channel", length = 24)
    private NotificationChannelPreference fallbackChannel;

    @Column(name = "allow_marketing_messages", nullable = false)
    private boolean allowMarketingMessages;

    @Column(name = "require_patient_consent", nullable = false)
    private boolean requirePatientConsent;

    @Column(name = "unsubscribe_footer_enabled", nullable = false)
    private boolean unsubscribeFooterEnabled;

    @Column(name = "max_messages_per_patient_per_day", nullable = false)
    private int maxMessagesPerPatientPerDay;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected TenantNotificationSettingsEntity() {
    }

    public static TenantNotificationSettingsEntity createDefault(UUID tenantId, UUID actorId) {
        TenantNotificationSettingsEntity row = new TenantNotificationSettingsEntity();
        row.id = UUID.randomUUID();
        row.tenantId = tenantId;
        row.emailEnabled = true;
        row.smsEnabled = false;
        row.whatsappEnabled = false;
        row.inAppEnabled = true;
        row.appointmentRemindersEnabled = true;
        row.appointmentReminder24hEnabled = true;
        row.appointmentReminder2hEnabled = true;
        row.followUpRemindersEnabled = true;
        row.billingRemindersEnabled = true;
        row.refillRemindersEnabled = true;
        row.vaccinationRemindersEnabled = true;
        row.leadFollowUpRemindersEnabled = true;
        row.webinarRemindersEnabled = true;
        row.birthdayWellnessEnabled = true;
        row.quietHoursEnabled = false;
        row.quietHoursStart = null;
        row.quietHoursEnd = null;
        row.timezone = "UTC";
        row.defaultChannel = NotificationChannelPreference.EMAIL;
        row.fallbackChannel = NotificationChannelPreference.SMS;
        row.allowMarketingMessages = false;
        row.requirePatientConsent = true;
        row.unsubscribeFooterEnabled = true;
        row.maxMessagesPerPatientPerDay = 5;
        row.createdAt = OffsetDateTime.now();
        row.updatedAt = row.createdAt;
        row.createdBy = actorId;
        row.updatedBy = actorId;
        return row;
    }

    public void updateFrom(
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
            UUID actorId
    ) {
        this.emailEnabled = emailEnabled;
        this.smsEnabled = smsEnabled;
        this.whatsappEnabled = whatsappEnabled;
        this.inAppEnabled = inAppEnabled;
        this.appointmentRemindersEnabled = appointmentRemindersEnabled;
        this.appointmentReminder24hEnabled = appointmentReminder24hEnabled;
        this.appointmentReminder2hEnabled = appointmentReminder2hEnabled;
        this.followUpRemindersEnabled = followUpRemindersEnabled;
        this.billingRemindersEnabled = billingRemindersEnabled;
        this.refillRemindersEnabled = refillRemindersEnabled;
        this.vaccinationRemindersEnabled = vaccinationRemindersEnabled;
        this.leadFollowUpRemindersEnabled = leadFollowUpRemindersEnabled;
        this.webinarRemindersEnabled = webinarRemindersEnabled;
        this.birthdayWellnessEnabled = birthdayWellnessEnabled;
        this.quietHoursEnabled = quietHoursEnabled;
        this.quietHoursStart = quietHoursStart;
        this.quietHoursEnd = quietHoursEnd;
        this.timezone = timezone;
        this.defaultChannel = defaultChannel;
        this.fallbackChannel = fallbackChannel;
        this.allowMarketingMessages = allowMarketingMessages;
        this.requirePatientConsent = requirePatientConsent;
        this.unsubscribeFooterEnabled = unsubscribeFooterEnabled;
        this.maxMessagesPerPatientPerDay = maxMessagesPerPatientPerDay;
        this.updatedAt = OffsetDateTime.now();
        this.updatedBy = actorId;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public boolean isEmailEnabled() { return emailEnabled; }
    public boolean isSmsEnabled() { return smsEnabled; }
    public boolean isWhatsappEnabled() { return whatsappEnabled; }
    public boolean isInAppEnabled() { return inAppEnabled; }
    public boolean isAppointmentRemindersEnabled() { return appointmentRemindersEnabled; }
    public boolean isAppointmentReminder24hEnabled() { return appointmentReminder24hEnabled; }
    public boolean isAppointmentReminder2hEnabled() { return appointmentReminder2hEnabled; }
    public boolean isFollowUpRemindersEnabled() { return followUpRemindersEnabled; }
    public boolean isBillingRemindersEnabled() { return billingRemindersEnabled; }
    public boolean isRefillRemindersEnabled() { return refillRemindersEnabled; }
    public boolean isVaccinationRemindersEnabled() { return vaccinationRemindersEnabled; }
    public boolean isLeadFollowUpRemindersEnabled() { return leadFollowUpRemindersEnabled; }
    public boolean isWebinarRemindersEnabled() { return webinarRemindersEnabled; }
    public boolean isBirthdayWellnessEnabled() { return birthdayWellnessEnabled; }
    public boolean isQuietHoursEnabled() { return quietHoursEnabled; }
    public LocalTime getQuietHoursStart() { return quietHoursStart; }
    public LocalTime getQuietHoursEnd() { return quietHoursEnd; }
    public String getTimezone() { return timezone; }
    public NotificationChannelPreference getDefaultChannel() { return defaultChannel; }
    public NotificationChannelPreference getFallbackChannel() { return fallbackChannel; }
    public boolean isAllowMarketingMessages() { return allowMarketingMessages; }
    public boolean isRequirePatientConsent() { return requirePatientConsent; }
    public boolean isUnsubscribeFooterEnabled() { return unsubscribeFooterEnabled; }
    public int getMaxMessagesPerPatientPerDay() { return maxMessagesPerPatientPerDay; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getUpdatedBy() { return updatedBy; }
}
