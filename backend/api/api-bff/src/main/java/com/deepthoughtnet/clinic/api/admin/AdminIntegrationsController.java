package com.deepthoughtnet.clinic.api.admin;

import com.deepthoughtnet.clinic.api.admin.dto.AdminIntegrationsDtos.IntegrationStatusResponse;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administration integrations status endpoints.
 */
@RestController
@RequestMapping("/api/admin/integrations")
public class AdminIntegrationsController {
    private final AdminIntegrationsStatusService integrationsStatusService;

    public AdminIntegrationsController(AdminIntegrationsStatusService integrationsStatusService) {
        this.integrationsStatusService = integrationsStatusService;
    }

    @GetMapping("/status")
    @PreAuthorize("@permissionChecker.hasRole('CLINIC_ADMIN') or @permissionChecker.hasRole('AUDITOR') or @permissionChecker.hasRole('PLATFORM_ADMIN') or @permissionChecker.hasRole('PLATFORM_TENANT_SUPPORT')")
    public IntegrationStatusResponse status() {
        var tenantId = RequestContextHolder.requireTenantId();
        return new IntegrationStatusResponse(integrationsStatusService.status(tenantId));
    }
}
