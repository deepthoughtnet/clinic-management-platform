package com.deepthoughtnet.clinic.carepilot.engagement.analytics;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Aggregated engagement metrics for CarePilot overview dashboards.
 */
public record PatientEngagementOverviewRecord(
        long totalActivePatients,
        long highEngagementCount,
        long mediumEngagementCount,
        long lowEngagementCount,
        long criticalEngagementCount,
        long inactivePatientsCount,
        long highRiskPatientsCount,
        long refillRiskCount,
        long followUpRiskCount,
        long overdueVaccinationCount,
        long overdueBillsRiskCount,
        Map<String, Long> engagementDistribution,
        Map<String, Long> cohortCounts,
        OffsetDateTime generatedAt
) {}
