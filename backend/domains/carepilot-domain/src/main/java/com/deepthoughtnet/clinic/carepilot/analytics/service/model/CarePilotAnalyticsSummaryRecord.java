package com.deepthoughtnet.clinic.carepilot.analytics.service.model;

import com.deepthoughtnet.clinic.carepilot.execution.service.model.CampaignExecutionRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Read-only analytics snapshot summarizing CarePilot campaign and execution health.
 */
public record CarePilotAnalyticsSummaryRecord(
        LocalDate startDate,
        LocalDate endDate,
        long totalCampaigns,
        long activeCampaigns,
        long totalExecutions,
        long pendingExecutions,
        long scheduledExecutions,
        long successfulExecutions,
        long failedExecutions,
        long retryingExecutions,
        long skippedExecutions,
        long deliveredExecutions,
        long readExecutions,
        long bouncedExecutions,
        long undeliveredExecutions,
        double successRate,
        double failureRate,
        double retryRate,
        Map<String, Long> executionsByStatus,
        Map<String, Long> executionsByChannel,
        List<CampaignExecutionBreakdownRecord> executionsByCampaign,
        List<ProviderFailureSummaryRecord> providerFailureSummary,
        List<CampaignExecutionRecord> recentFailures,
        List<CampaignExecutionRecord> recentSuccesses
) {}
