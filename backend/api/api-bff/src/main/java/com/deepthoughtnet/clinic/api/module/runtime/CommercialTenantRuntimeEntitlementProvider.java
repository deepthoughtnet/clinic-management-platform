package com.deepthoughtnet.clinic.api.module.runtime;

import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementService;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CommercialTenantRuntimeEntitlementProvider implements TenantRuntimeEntitlementProvider {
    private final CommercialEffectiveEntitlementService commercialEffectiveEntitlementService;

    public CommercialTenantRuntimeEntitlementProvider(CommercialEffectiveEntitlementService commercialEffectiveEntitlementService) {
        this.commercialEffectiveEntitlementService = commercialEffectiveEntitlementService;
    }

    @Override
    public void requireTenantActive(UUID tenantId) {
        if (!commercialEffectiveEntitlementService.isCommercialRuntimeEnabledForTenant(tenantId)) {
            throw new com.deepthoughtnet.clinic.platform.core.errors.ForbiddenException("Commercial runtime is disabled for tenant");
        }
    }

    @Override
    public void requireModuleEnabled(UUID tenantId, String moduleCode) {
        if (!isModuleEnabled(tenantId, moduleCode)) {
            throw new com.deepthoughtnet.clinic.platform.core.errors.ForbiddenException("Module is disabled: " + moduleCode);
        }
    }

    @Override
    public boolean isModuleEnabled(UUID tenantId, String moduleCode) {
        return commercialEffectiveEntitlementService.isModuleEnabled(tenantId, moduleCode);
    }

    @Override
    public boolean isFeatureEnabled(UUID tenantId, String featureCode) {
        return commercialEffectiveEntitlementService.isFeatureEnabled(tenantId, featureCode);
    }
}
