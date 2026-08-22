package com.deepthoughtnet.clinic.api.admin;

import com.deepthoughtnet.clinic.api.admin.dto.AdminIntegrationsDtos.IntegrationStatusResponse;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
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
    @PreAuthorize("@permissionChecker.hasRole('PLATFORM_ADMIN')")
    public IntegrationStatusResponse status() {
        RequestContext context = RequestContextHolder.require();
        var tenantId = context.tenantId().value();
        boolean includeTechnicalDetails = context.tokenRoles() != null && context.tokenRoles().stream()
                .anyMatch(role -> "PLATFORM_ADMIN".equalsIgnoreCase(role) || "PLATFORM_TENANT_SUPPORT".equalsIgnoreCase(role));
        return new IntegrationStatusResponse(integrationsStatusService.status(tenantId, includeTechnicalDetails));
    }
}
