package com.deepthoughtnet.clinic.api.admin;

import com.deepthoughtnet.clinic.api.admin.dto.AdminNotificationSettingsDtos.NotificationSettingsResponse;
import com.deepthoughtnet.clinic.api.admin.dto.AdminNotificationSettingsDtos.UpdateNotificationSettingsRequest;
import com.deepthoughtnet.clinic.api.carepilot.CarePilotMessagingStatusService;
import com.deepthoughtnet.clinic.api.carepilot.dto.MessagingDtos.ProviderReadinessStatus;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.service.TenantNotificationSettingsService;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.service.model.NotificationSettingsRecord;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.service.model.NotificationSettingsUpdateCommand;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administration APIs for tenant notification settings and provider-readiness warnings.
 */
@RestController
@RequestMapping("/api/admin/notification-settings")
public class AdminNotificationSettingsController {
    private final TenantNotificationSettingsService settingsService;
    private final CarePilotMessagingStatusService messagingStatusService;

    public AdminNotificationSettingsController(
            TenantNotificationSettingsService settingsService,
            CarePilotMessagingStatusService messagingStatusService
    ) {
        this.settingsService = settingsService;
        this.messagingStatusService = messagingStatusService;
    }

    @GetMapping
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('RECEPTIONIST') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public NotificationSettingsResponse get() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return toResponse(settingsService.getOrCreate(tenantId));
    }

    @PutMapping
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public NotificationSettingsResponse update(@RequestBody UpdateNotificationSettingsRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorId = RequestContextHolder.require().appUserId();
        NotificationSettingsRecord updated = settingsService.update(tenantId, new NotificationSettingsUpdateCommand(
                request.emailEnabled(),
                request.smsEnabled(),
                request.whatsappEnabled(),
                request.inAppEnabled(),
                request.appointmentRemindersEnabled(),
                request.appointmentReminder24hEnabled(),
                request.appointmentReminder2hEnabled(),
                request.followUpRemindersEnabled(),
                request.billingRemindersEnabled(),
                request.refillRemindersEnabled(),
                request.vaccinationRemindersEnabled(),
                request.leadFollowUpRemindersEnabled(),
                request.webinarRemindersEnabled(),
                request.birthdayWellnessEnabled(),
                request.quietHoursEnabled(),
                request.quietHoursStart(),
                request.quietHoursEnd(),
                request.timezone(),
                request.defaultChannel(),
                request.fallbackChannel(),
                request.allowMarketingMessages(),
                request.requirePatientConsent(),
                request.unsubscribeFooterEnabled(),
                request.maxMessagesPerPatientPerDay()
        ), actorId);
        return toResponse(updated);
    }

    private NotificationSettingsResponse toResponse(NotificationSettingsRecord record) {
        var statuses = messagingStatusService.providerStatuses();
        boolean emailReady = statuses.stream().anyMatch(s -> s.channel().name().equals("EMAIL") && s.status() == ProviderReadinessStatus.READY);
        boolean smsReady = statuses.stream().anyMatch(s -> s.channel().name().equals("SMS") && s.status() == ProviderReadinessStatus.READY);
        boolean whatsappReady = statuses.stream().anyMatch(s -> s.channel().name().equals("WHATSAPP") && s.status() == ProviderReadinessStatus.READY);
        var warnings = settingsService.computeWarnings(record, emailReady, smsReady, whatsappReady);
        return new NotificationSettingsResponse(
                record.id(),
                record.tenantId(),
                record.emailEnabled(),
                record.smsEnabled(),
                record.whatsappEnabled(),
                record.inAppEnabled(),
                record.appointmentRemindersEnabled(),
                record.appointmentReminder24hEnabled(),
                record.appointmentReminder2hEnabled(),
                record.followUpRemindersEnabled(),
                record.billingRemindersEnabled(),
                record.refillRemindersEnabled(),
                record.vaccinationRemindersEnabled(),
                record.leadFollowUpRemindersEnabled(),
                record.webinarRemindersEnabled(),
                record.birthdayWellnessEnabled(),
                record.quietHoursEnabled(),
                record.quietHoursStart(),
                record.quietHoursEnd(),
                record.timezone(),
                record.defaultChannel(),
                record.fallbackChannel(),
                record.allowMarketingMessages(),
                record.requirePatientConsent(),
                record.unsubscribeFooterEnabled(),
                record.maxMessagesPerPatientPerDay(),
                record.createdAt(),
                record.updatedAt(),
                record.createdBy(),
                record.updatedBy(),
                emailReady,
                smsReady,
                whatsappReady,
                warnings
        );
    }
}
