package com.deepthoughtnet.clinic.api.carepilot;

import com.deepthoughtnet.clinic.api.carepilot.dto.ExecutionDtos.ExecutionResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.RemindersDtos.ReminderDetailResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.RemindersDtos.ReminderListResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.RemindersDtos.ReminderMutationRequest;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignType;
import com.deepthoughtnet.clinic.carepilot.execution.service.CampaignExecutionService;
import com.deepthoughtnet.clinic.carepilot.execution.service.model.CampaignExecutionRecord;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operational reminders management APIs for CarePilot reminder executions.
 */
@RestController
@RequestMapping("/api/carepilot/reminders")
public class CarePilotRemindersController {
    private final CarePilotRemindersService remindersService;
    private final CampaignExecutionService executionService;

    public CarePilotRemindersController(CarePilotRemindersService remindersService, CampaignExecutionService executionService) {
        this.remindersService = remindersService;
        this.executionService = executionService;
    }

    /**
     * Lists reminder executions with operational filters.
     */
    @GetMapping
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public ReminderListResponse list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID campaignId,
            @RequestParam(required = false) CampaignType campaignType,
            @RequestParam(required = false) ChannelType channel,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) String patientName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String providerName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return remindersService.list(tenantId, status, campaignId, campaignType, channel, patientId, patientName, fromDate, toDate, providerName, page, size);
    }

    /**
     * Returns reminder execution detail including timeline/attempts/events.
     */
    @GetMapping("/{executionId}")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public ReminderDetailResponse detail(@PathVariable UUID executionId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return remindersService.detail(tenantId, executionId);
    }

    /**
     * Reuses existing execution retry flow from reminders management context.
     */
    @PatchMapping("/{executionId}/retry")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public ExecutionResponse retry(@PathVariable UUID executionId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        CampaignExecutionRecord record = executionService.retryExecution(tenantId, executionId);
        return toResponse(record);
    }

    /**
     * Reuses existing execution resend alias flow from reminders management context.
     */
    @PatchMapping("/{executionId}/resend")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public ExecutionResponse resend(@PathVariable UUID executionId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        CampaignExecutionRecord record = executionService.retryExecution(tenantId, executionId);
        return toResponse(record);
    }

    /**
     * Cancels a queued/retrying reminder execution and prevents further processing.
     */
    @PostMapping("/{executionId}/cancel")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public ExecutionResponse cancel(@PathVariable UUID executionId, @RequestBody(required = false) ReminderMutationRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        CampaignExecutionRecord record = executionService.cancelExecution(tenantId, executionId, request == null ? null : request.reason());
        return toResponse(record);
    }

    /**
     * Suppresses a queued/retrying reminder execution and prevents future pickup.
     */
    @PostMapping("/{executionId}/suppress")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public ExecutionResponse suppress(@PathVariable UUID executionId, @RequestBody(required = false) ReminderMutationRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        CampaignExecutionRecord record = executionService.suppressExecution(tenantId, executionId, request == null ? null : request.reason());
        return toResponse(record);
    }

    /**
     * Reschedules a queued/retrying reminder execution to a new future timestamp.
     */
    @PostMapping("/{executionId}/reschedule")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public ExecutionResponse reschedule(@PathVariable UUID executionId, @RequestBody ReminderMutationRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        CampaignExecutionRecord record = executionService.rescheduleExecution(
                tenantId,
                executionId,
                request == null ? null : request.newScheduledAt(),
                request == null ? null : request.reason()
        );
        return toResponse(record);
    }

    private ExecutionResponse toResponse(CampaignExecutionRecord record) {
        return new ExecutionResponse(
                record.id(), record.tenantId(), record.campaignId(), record.templateId(), record.channelType(),
                record.recipientPatientId(), record.scheduledAt(), record.status(), record.attemptCount(), record.lastError(),
                record.executedAt(), record.nextAttemptAt(), record.deliveryStatus(), record.providerName(),
                record.providerMessageId(), record.sourceType(), record.sourceReferenceId(), record.reminderWindow(),
                record.referenceDateTime(), record.lastAttemptAt(), record.failureReason(), record.createdAt(), record.updatedAt()
        );
    }
}
