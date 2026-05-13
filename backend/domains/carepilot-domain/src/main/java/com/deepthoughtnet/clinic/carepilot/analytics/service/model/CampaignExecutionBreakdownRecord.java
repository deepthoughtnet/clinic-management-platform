package com.deepthoughtnet.clinic.carepilot.analytics.service.model;

import java.util.UUID;

/**
 * Campaign-level execution aggregate used by analytics summary.
 */
public record CampaignExecutionBreakdownRecord(
        UUID campaignId,
        String campaignName,
        long totalExecutions,
        long successfulExecutions,
        long failedExecutions,
        double successRate
) {}
