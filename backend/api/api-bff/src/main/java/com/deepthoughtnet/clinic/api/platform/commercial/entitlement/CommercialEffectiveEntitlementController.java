package com.deepthoughtnet.clinic.api.platform.commercial.entitlement;

import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.ComparisonResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.CreateOverrideRequest;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.EffectiveEntitlementSnapshotResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.EffectiveLimitResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.OverrideHistoryResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.OverrideImpactPreviewResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.LegacyComparisonResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.OverrideResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.PageResponse;
import com.deepthoughtnet.clinic.api.platform.commercial.entitlement.dto.CommercialEntitlementDtos.UpdateOverrideRequest;
import com.deepthoughtnet.clinic.api.platform.service.TenantModuleService;
import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementEnums.GenerationReason;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/commercial/tenants/{tenantId}")
@PreAuthorize("@permissionChecker.hasPermission('commercial.entitlements.view')")
public class CommercialEffectiveEntitlementController {
    private final CommercialEffectiveEntitlementApiService service;
    private final TenantModuleService tenantModuleService;

    public CommercialEffectiveEntitlementController(CommercialEffectiveEntitlementApiService service, TenantModuleService tenantModuleService) {
        this.service = service;
        this.tenantModuleService = tenantModuleService;
    }

    @GetMapping("/effective-entitlements")
    public EffectiveEntitlementSnapshotResponse getEffectiveEntitlements(@PathVariable UUID tenantId) {
        return service.getCurrentSnapshot(tenantId);
    }

    @PostMapping("/effective-entitlements/regenerate")
    @PreAuthorize("@permissionChecker.hasPermission('commercial.entitlements.regenerate')")
    public EffectiveEntitlementSnapshotResponse regenerate(@PathVariable UUID tenantId, @RequestParam(required = false) GenerationReason reason) {
        return service.regenerate(tenantId, reason == null ? GenerationReason.MANUAL_REGENERATE : reason);
    }

    @GetMapping("/effective-entitlements/history")
    public PageResponse<EffectiveEntitlementSnapshotResponse> history(@PathVariable UUID tenantId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.history(tenantId, page, size);
    }

    @GetMapping("/effective-entitlements/legacy-comparison")
    @PreAuthorize("@permissionChecker.hasPermission('commercial.runtime.diagnostics.view')")
    public LegacyComparisonResponse compareWithLegacy(@PathVariable UUID tenantId) {
        return service.compareWithLegacy(tenantId, new LinkedHashMap<>(tenantModuleService.findForTenant(tenantId)));
    }

    @GetMapping("/overrides")
    @PreAuthorize("@permissionChecker.hasPermission('commercial.overrides.view')")
    public List<OverrideResponse> listOverrides(@PathVariable UUID tenantId) {
        return service.listOverrides(tenantId);
    }

    @PostMapping("/overrides")
    @PreAuthorize("@permissionChecker.hasPermission('commercial.overrides.create')")
    public OverrideResponse createOverride(@PathVariable UUID tenantId, @RequestBody CreateOverrideRequest request) {
        return service.createOverride(tenantId, request);
    }

    @PutMapping("/overrides/{overrideId}")
    @PreAuthorize("@permissionChecker.hasPermission('commercial.overrides.create')")
    public OverrideResponse updateOverride(@PathVariable UUID tenantId, @PathVariable UUID overrideId, @RequestBody UpdateOverrideRequest request) {
        return service.updateOverride(tenantId, overrideId, request);
    }

    @PostMapping("/overrides/impact-preview")
    @PreAuthorize("@permissionChecker.hasPermission('commercial.overrides.view')")
    public OverrideImpactPreviewResponse previewOverride(@PathVariable UUID tenantId, @RequestBody CreateOverrideRequest request) {
        return service.previewOverride(tenantId, request);
    }

    @GetMapping("/overrides/{overrideId}/history")
    @PreAuthorize("@permissionChecker.hasPermission('commercial.overrides.view')")
    public List<OverrideHistoryResponse> overrideHistory(@PathVariable UUID tenantId, @PathVariable UUID overrideId) {
        return service.getOverrideHistory(tenantId, overrideId);
    }

    @PostMapping("/overrides/{overrideId}/submit")
    @PreAuthorize("@permissionChecker.hasPermission('commercial.overrides.submit')")
    public OverrideResponse submitOverride(@PathVariable UUID tenantId, @PathVariable UUID overrideId) {
        return service.submitOverride(tenantId, overrideId);
    }

    @PostMapping("/overrides/{overrideId}/withdraw")
    @PreAuthorize("@permissionChecker.hasPermission('commercial.overrides.create')")
    public OverrideResponse withdrawOverride(@PathVariable UUID tenantId, @PathVariable UUID overrideId) {
        return service.withdrawOverride(tenantId, overrideId);
    }

    @PostMapping("/overrides/{overrideId}/approve")
    @PreAuthorize("@permissionChecker.hasPermission('commercial.overrides.review')")
    public OverrideResponse approveOverride(@PathVariable UUID tenantId, @PathVariable UUID overrideId, @RequestParam(required = false) String remarks) {
        return service.approveOverride(tenantId, overrideId, remarks);
    }

    @PostMapping("/overrides/{overrideId}/request-changes")
    @PreAuthorize("@permissionChecker.hasPermission('commercial.overrides.review')")
    public OverrideResponse requestChanges(@PathVariable UUID tenantId, @PathVariable UUID overrideId, @RequestParam(required = false) String remarks) {
        return service.requestChanges(tenantId, overrideId, remarks);
    }

    @PostMapping("/overrides/{overrideId}/activate")
    @PreAuthorize("@permissionChecker.hasPermission('commercial.overrides.activate')")
    public OverrideResponse activateOverride(@PathVariable UUID tenantId, @PathVariable UUID overrideId) {
        return service.activateOverride(tenantId, overrideId);
    }

    @PostMapping("/overrides/{overrideId}/cancel")
    @PreAuthorize("@permissionChecker.hasPermission('commercial.overrides.cancel')")
    public OverrideResponse cancelOverride(@PathVariable UUID tenantId, @PathVariable UUID overrideId) {
        return service.cancelOverride(tenantId, overrideId);
    }

    @PostMapping("/overrides/{overrideId}/rollback")
    @PreAuthorize("@permissionChecker.hasPermission('commercial.overrides.rollback')")
    public OverrideResponse rollbackOverride(@PathVariable UUID tenantId, @PathVariable UUID overrideId, @RequestParam(required = false) String reason) {
        return service.rollbackOverride(tenantId, overrideId, reason);
    }

    @GetMapping("/limits/{limitCode}")
    @PreAuthorize("@permissionChecker.hasPermission('commercial.entitlements.view')")
    public EffectiveLimitResponse getLimit(@PathVariable UUID tenantId, @PathVariable String limitCode) {
        return service.getLimit(tenantId, limitCode);
    }
}
