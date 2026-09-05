package com.deepthoughtnet.clinic.api.ops;

import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.PlatformOperationsOverviewResponse;
import com.deepthoughtnet.clinic.api.ops.dto.PlatformOpsDtos.PlatformOperationsDiagnosticResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Platform-wide operations overview for the top-level admin control center. */
@RestController
@RequestMapping("/api/platform/operations")
public class PlatformOperationsOverviewController {
    private final PlatformOperationsOverviewService overviewService;
    private final PlatformOperationsDiagnosticsService diagnosticsService;

    public PlatformOperationsOverviewController(PlatformOperationsOverviewService overviewService,
                                                PlatformOperationsDiagnosticsService diagnosticsService) {
        this.overviewService = overviewService;
        this.diagnosticsService = diagnosticsService;
    }

    @GetMapping("/overview")
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN')")
    public PlatformOperationsOverviewResponse overview() {
        return overviewService.overview();
    }

    @PostMapping("/health/{component}/test")
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN')")
    public PlatformOperationsDiagnosticResponse testComponent(@PathVariable String component) {
        return diagnosticsService.test(component);
    }
}
