package com.deepthoughtnet.clinic.api.platform.commercial.entitlement;

import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.LegacyComparisonResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.RuntimeDiffSummaryResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.RuntimeDiffTenantResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/commercial/runtime-diff")
@PreAuthorize("@permissionChecker.hasPermission('commercial.runtime.readiness.view')")
public class CommercialRuntimeDiffController {
    private final CommercialRuntimeDiffApiService service;

    public CommercialRuntimeDiffController(CommercialRuntimeDiffApiService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public RuntimeDiffSummaryResponse summary() {
        return service.summary();
    }

    @GetMapping("/tenants")
    public List<RuntimeDiffTenantResponse> tenants() {
        return service.tenants();
    }

    @GetMapping("/tenants/{tenantId}")
    public RuntimeDiffTenantResponse tenant(@PathVariable UUID tenantId) {
        return service.tenant(tenantId);
    }

    @PostMapping("/tenants/{tenantId}/compare")
    @PreAuthorize("@permissionChecker.hasPermission('commercial.runtime.diagnostics.view')")
    public LegacyComparisonResponse compare(@PathVariable UUID tenantId) {
        return service.compare(tenantId);
    }
}
