package com.deepthoughtnet.clinic.api.admin;

import com.deepthoughtnet.clinic.api.admin.dto.AdminNotificationSettingsDtos.NotificationSettingsResponse;
import com.deepthoughtnet.clinic.api.admin.dto.AdminNotificationSettingsDtos.UpdateNotificationSettingsRequest;
import com.deepthoughtnet.clinic.api.carepilot.CarePilotMessagingStatusService;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.api.carepilot.dto.MessagingDtos.ProviderReadinessStatus;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.service.TenantNotificationSettingsService;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.service.model.NotificationSettingsRecord;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.service.model.NotificationSettingsUpdateCommand;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(AdminNotificationSettingsController.class);
    private final TenantNotificationSettingsService settingsService;
    private final CarePilotMessagingStatusService messagingStatusService;
    private final ClinicTimeZoneResolver clinicTimeZoneResolver;

    public AdminNotificationSettingsController(
            TenantNotificationSettingsService settingsService,
            CarePilotMessagingStatusService messagingStatusService,
            ClinicTimeZoneResolver clinicTimeZoneResolver
    ) {
        this.settingsService = settingsService;
        this.messagingStatusService = messagingStatusService;
        this.clinicTimeZoneResolver = clinicTimeZoneResolver;
    }

    @GetMapping
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('RECEPTIONIST') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public NotificationSettingsResponse get() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        String actorRole = RequestContextHolder.require().tenantRole();
        if (log.isInfoEnabled()) {
            log.info("admin.notification-settings request tenantId={} role={}", tenantId, actorRole);
        }
        return toResponse(settingsService.getOrCreate(tenantId));
    }

    @PutMapping
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public NotificationSettingsResponse update(@RequestBody UpdateNotificationSettingsRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorId = RequestContextHolder.require().appUserId();
        String normalizedTimezone = clinicTimeZoneResolver.normalizeForPersistence(tenantId, request.timezone());
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
                normalizedTimezone,
                request.defaultChannel(),
                request.fallbackChannel(),
                request.allowMarketingMessages(),
                request.requirePatientConsent(),
                request.unsubscribeFooterEnabled(),
                request.maxMessagesPerPatientPerDay()
        ), actorId);
        if (log.isDebugEnabled()) {
            log.debug("admin.notification-settings update tenantId={} requestedTimezone={} persistedTimezone={}",
                    tenantId, request.timezone(), normalizedTimezone);
        }
        return toResponse(updated);
    }

    private NotificationSettingsResponse toResponse(NotificationSettingsRecord record) {
        var statuses = messagingStatusService.providerStatuses();
        boolean emailReady = statuses.stream().anyMatch(s -> s.channel().name().equals("EMAIL") && s.status() == ProviderReadinessStatus.READY);
        boolean smsReady = statuses.stream().anyMatch(s -> s.channel().name().equals("SMS") && s.status() == ProviderReadinessStatus.READY);
        boolean whatsappReady = statuses.stream().anyMatch(s -> s.channel().name().equals("WHATSAPP") && s.status() == ProviderReadinessStatus.READY);
        var warnings = settingsService.computeWarnings(record, emailReady, smsReady, whatsappReady);
        ZoneId effectiveZone = clinicTimeZoneResolver.resolve(record.tenantId(), record.timezone());
        String effectiveTimezone = effectiveZone.getId();
        Instant now = Instant.now();
        OffsetDateTime serverNowUtc = now.atOffset(ZoneOffset.UTC);
        OffsetDateTime clinicNow = now.atZone(effectiveZone).toOffsetDateTime();
        if (log.isInfoEnabled()) {
            log.info("admin.notification-settings response tenantId={} storedTimezone={} effectiveTimezone={} serverNowUtc={} clinicNow={}",
                    record.tenantId(), record.timezone(), effectiveTimezone, serverNowUtc, clinicNow);
        }
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
                effectiveTimezone,
                effectiveTimezone,
                clinicNow,
                serverNowUtc,
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
