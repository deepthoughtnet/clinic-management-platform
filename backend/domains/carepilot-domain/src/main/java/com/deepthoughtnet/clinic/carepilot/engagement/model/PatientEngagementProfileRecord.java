package com.deepthoughtnet.clinic.carepilot.engagement.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Computed V1 patient engagement profile used for analytics, cohorts, and operations.
 */
public record PatientEngagementProfileRecord(
        UUID patientId,
        UUID tenantId,
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
