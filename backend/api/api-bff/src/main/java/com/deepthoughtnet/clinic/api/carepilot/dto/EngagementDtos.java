package com.deepthoughtnet.clinic.api.carepilot.dto;

import com.deepthoughtnet.clinic.carepilot.engagement.model.EngagementCohortType;
import com.deepthoughtnet.clinic.carepilot.engagement.model.EngagementLevel;
import com.deepthoughtnet.clinic.carepilot.engagement.model.RiskLevel;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * API payloads for CarePilot patient engagement views.
 */
public final class EngagementDtos {
    private EngagementDtos() {}

    /** Patient-level engagement profile for operations and segmentation. */
    public record EngagementProfileResponse(
            String patientId,
            String tenantId,
            String patientNumber,
            String patientName,
            String patientEmail,
            String patientMobile,
            int engagementScore,
            EngagementLevel engagementLevel,
            RiskLevel inactiveRisk,
            RiskLevel noShowRisk,
            RiskLevel refillRisk,
            RiskLevel followUpRisk,
            RiskLevel overdueBalanceRisk,
            RiskLevel vaccinationCompliance,
            LocalDate lastAppointmentAt,
            LocalDate lastConsultationAt,
            OffsetDateTime lastCampaignEngagementAt,
            int missedAppointmentsCount,
            int completedAppointmentsCount,
            int overdueBillsCount,
            int overdueVaccinationsCount,
            int pendingRefillCount,
            int followUpMissedCount,
            boolean inactive,
            List<String> riskReasons,
            String suggestedCampaignType,
            OffsetDateTime generatedAt
    ) {}

    /** Aggregate engagement summary for dashboards. */
    public record EngagementOverviewResponse(
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

    /** Cohort listing with lightweight pagination metadata. */
    public record EngagementCohortResponse(
            EngagementCohortType cohort,
            int offset,
            int limit,
            int count,
            List<EngagementProfileResponse> rows,
            OffsetDateTime generatedAt
    ) {}
}
