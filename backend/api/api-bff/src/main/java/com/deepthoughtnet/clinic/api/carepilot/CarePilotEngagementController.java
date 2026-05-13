package com.deepthoughtnet.clinic.api.carepilot;

import com.deepthoughtnet.clinic.api.carepilot.dto.EngagementDtos.EngagementCohortResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.EngagementDtos.EngagementOverviewResponse;
import com.deepthoughtnet.clinic.api.carepilot.dto.EngagementDtos.EngagementProfileResponse;
import com.deepthoughtnet.clinic.carepilot.engagement.model.EngagementCohortType;
import com.deepthoughtnet.clinic.carepilot.engagement.model.PatientEngagementProfileRecord;
import com.deepthoughtnet.clinic.carepilot.engagement.service.PatientEngagementService;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant-scoped patient engagement APIs for CarePilot analytics and operations.
 */
@RestController
@RequestMapping("/api/carepilot/engagement")
public class CarePilotEngagementController {
    private final PatientEngagementService engagementService;

    public CarePilotEngagementController(PatientEngagementService engagementService) {
        this.engagementService = engagementService;
    }

    /** Returns aggregated engagement overview metrics. */
    @GetMapping("/overview")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public EngagementOverviewResponse overview() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        var record = engagementService.overview(tenantId);
        return new EngagementOverviewResponse(
                record.totalActivePatients(),
                record.highEngagementCount(),
                record.mediumEngagementCount(),
                record.lowEngagementCount(),
                record.criticalEngagementCount(),
                record.inactivePatientsCount(),
                record.highRiskPatientsCount(),
                record.refillRiskCount(),
                record.followUpRiskCount(),
                record.overdueVaccinationCount(),
                record.overdueBillsRiskCount(),
                record.engagementDistribution(),
                record.cohortCounts(),
                record.generatedAt()
        );
    }

    /** Returns cohort members for one engagement segment. */
    @GetMapping("/cohorts")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public EngagementCohortResponse cohorts(
            @RequestParam EngagementCohortType cohort,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit
    ) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        List<EngagementProfileResponse> rows = engagementService.cohort(tenantId, cohort, offset, limit).stream()
                .map(this::toResponse)
                .toList();
        return new EngagementCohortResponse(cohort, Math.max(0, offset), Math.max(1, Math.min(200, limit)), rows.size(), rows, OffsetDateTime.now());
    }

    /** Returns high-risk patients. */
    @GetMapping("/high-risk")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public EngagementCohortResponse highRisk(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return cohorts(EngagementCohortType.HIGH_RISK_PATIENTS, offset, limit);
    }

    /** Returns inactive patients according to current engagement thresholds. */
    @GetMapping("/inactive")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public EngagementCohortResponse inactive(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return cohorts(EngagementCohortType.INACTIVE_PATIENTS, offset, limit);
    }

    private EngagementProfileResponse toResponse(PatientEngagementProfileRecord row) {
        return new EngagementProfileResponse(
                row.patientId().toString(),
                row.tenantId().toString(),
                row.patientNumber(),
                row.patientName(),
                row.patientEmail(),
                row.patientMobile(),
                row.engagementScore(),
                row.engagementLevel(),
                row.inactiveRisk(),
                row.noShowRisk(),
                row.refillRisk(),
                row.followUpRisk(),
                row.overdueBalanceRisk(),
                row.vaccinationCompliance(),
                row.lastAppointmentAt(),
                row.lastConsultationAt(),
                row.lastCampaignEngagementAt(),
                row.missedAppointmentsCount(),
                row.completedAppointmentsCount(),
                row.overdueBillsCount(),
                row.overdueVaccinationsCount(),
                row.pendingRefillCount(),
                row.followUpMissedCount(),
                row.inactive(),
                row.riskReasons(),
                row.suggestedCampaignType(),
                row.generatedAt()
        );
    }
}
