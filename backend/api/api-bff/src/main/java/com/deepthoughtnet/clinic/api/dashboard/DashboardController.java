package com.deepthoughtnet.clinic.api.dashboard;

import com.deepthoughtnet.clinic.api.dashboard.dto.DashboardSummaryResponse;
import com.deepthoughtnet.clinic.api.reports.ReportingFacade;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final ReportingFacade reportingFacade;

    public DashboardController(ReportingFacade reportingFacade) {
        this.reportingFacade = reportingFacade;
    }

    @GetMapping("/summary")
    @PreAuthorize("@permissionChecker.hasPermission('dashboard.read') or @permissionChecker.hasPermission('report.read')")
    public DashboardSummaryResponse summary() {
        return reportingFacade.dashboardSummary(RequestContextHolder.requireTenantId());
    }
}
