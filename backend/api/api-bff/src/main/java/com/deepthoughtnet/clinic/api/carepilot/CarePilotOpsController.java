package com.deepthoughtnet.clinic.api.carepilot;

import com.deepthoughtnet.clinic.api.carepilot.dto.AnalyticsDtos.ExecutionTimelineResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.AnalyticsDtos.DeliveryEventResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.AnalyticsDtos.TimelineEventResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.ExecutionDtos.DeliveryAttemptResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.ExecutionDtos.ExecutionResponse;
import com.deepthoughtnet.clinic.carepilot.analytics.service.CarePilotAnalyticsService;
import com.deepthoughtnet.clinic.carepilot.analytics.service.model.CarePilotExecutionTimelineRecord;
import com.deepthoughtnet.clinic.carepilot.execution.model.ExecutionStatus;
import com.deepthoughtnet.clinic.carepilot.execution.service.model.CampaignExecutionRecord;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only operational APIs for CarePilot failed queues and execution timelines.
 */
@RestController
@RequestMapping("/api/carepilot/ops")
public class CarePilotOpsController {
    private final CarePilotAnalyticsService analyticsService;

    public CarePilotOpsController(CarePilotAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * Returns filtered failed/dead-letter execution queue for operations triage.
     */
    @GetMapping("/failed-executions")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public List<ExecutionResponse> failedExecutions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) UUID campaignId,
            @RequestParam(required = false) ChannelType channel,
            @RequestParam(required = false) ExecutionStatus status,
            @RequestParam(required = false) String providerName,
            @RequestParam(required = false, defaultValue = "false") boolean retryableOnly
    ) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return analyticsService.listFailedExecutions(tenantId, startDate, endDate, campaignId, channel, status, providerName, retryableOnly)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Returns execution details, delivery attempts and status events for one execution.
     */
    @GetMapping("/executions/{executionId}/timeline")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public ExecutionTimelineResponse timeline(@PathVariable UUID executionId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        CarePilotExecutionTimelineRecord record = analyticsService.timeline(tenantId, executionId);
        return new ExecutionTimelineResponse(
                toResponse(record.execution()),
                record.deliveryAttempts().stream().map(attempt -> new DeliveryAttemptResponse(
                        attempt.id(), attempt.tenantId(), attempt.executionId(), attempt.attemptNumber(), attempt.providerName(),
                        attempt.channelType(), attempt.deliveryStatus(), attempt.errorCode(), attempt.errorMessage(), attempt.attemptedAt()
                )).toList(),
                record.deliveryEvents().stream().map(event -> new DeliveryEventResponse(
                        event.id() == null ? null : event.id().toString(),
                        event.executionId() == null ? null : event.executionId().toString(),
                        event.providerName(),
                        event.providerMessageId(),
                        event.channelType(),
                        event.externalStatus(),
                        event.internalStatus(),
                        event.eventType(),
                        event.eventTimestamp(),
                        event.receivedAt()
                )).toList(),
                record.statusEvents().stream().map(event -> new TimelineEventResponse(event.type(), event.status(), event.detail(), event.at())).toList()
        );
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
