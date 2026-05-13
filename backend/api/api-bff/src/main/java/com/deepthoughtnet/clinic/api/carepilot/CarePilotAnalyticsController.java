package com.deepthoughtnet.clinic.api.carepilot;

import com.deepthoughtnet.clinic.api.carepilot.dto.AnalyticsDtos.AnalyticsSummaryResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.AnalyticsDtos.CampaignBreakdownResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.AnalyticsDtos.ProviderFailureSummaryResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.ExecutionDtos.ExecutionResponse;
import com.deepthoughtnet.clinic.carepilot.analytics.service.CarePilotAnalyticsService;
import com.deepthoughtnet.clinic.carepilot.analytics.service.model.CarePilotAnalyticsSummaryRecord;
import com.deepthoughtnet.clinic.carepilot.execution.service.model.CampaignExecutionRecord;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Exposes read-only CarePilot campaign analytics for tenant operators. */
@RestController
@RequestMapping("/api/carepilot/analytics")
public class CarePilotAnalyticsController {
    private final CarePilotAnalyticsService analyticsService;

    public CarePilotAnalyticsController(CarePilotAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * Returns aggregate execution and delivery analytics for a date range and optional campaign.
     */
    @GetMapping("/summary")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public AnalyticsSummaryResponse summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) UUID campaignId
    ) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        CarePilotAnalyticsSummaryRecord record = analyticsService.summary(tenantId, startDate, endDate, campaignId);
        return new AnalyticsSummaryResponse(
                record.startDate(),
                record.endDate(),
                record.totalCampaigns(),
                record.activeCampaigns(),
                record.totalExecutions(),
                record.pendingExecutions(),
                record.scheduledExecutions(),
                record.successfulExecutions(),
                record.failedExecutions(),
                record.retryingExecutions(),
                record.skippedExecutions(),
                record.deliveredExecutions(),
                record.readExecutions(),
                record.bouncedExecutions(),
                record.undeliveredExecutions(),
                record.successRate(),
                record.failureRate(),
                record.retryRate(),
                record.executionsByStatus(),
                record.executionsByChannel(),
                record.executionsByCampaign().stream().map(row -> new CampaignBreakdownResponse(
                        row.campaignId(), row.campaignName(), row.totalExecutions(), row.successfulExecutions(), row.failedExecutions(), row.successRate()
                )).toList(),
                record.providerFailureSummary().stream().map(row -> new ProviderFailureSummaryResponse(row.providerName(), row.failureCount())).toList(),
                record.recentFailures().stream().map(this::toResponse).toList(),
                record.recentSuccesses().stream().map(this::toResponse).toList()
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
