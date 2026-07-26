package com.deepthoughtnet.clinic.api.module.runtime;

import com.deepthoughtnet.clinic.identity.service.TenantSubscriptionService;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class LegacyTenantRuntimeEntitlementProvider implements TenantRuntimeEntitlementProvider {
    private final TenantSubscriptionService tenantSubscriptionService;

    public LegacyTenantRuntimeEntitlementProvider(TenantSubscriptionService tenantSubscriptionService) {
        this.tenantSubscriptionService = tenantSubscriptionService;
    }

    @Override
    public void requireTenantActive(UUID tenantId) {
        tenantSubscriptionService.requireTenantActive(tenantId);
    }

    @Override
    public void requireModuleEnabled(UUID tenantId, String moduleCode) {
        tenantSubscriptionService.requireModuleEnabled(tenantId, moduleCode);
    }

    @Override
    public boolean isModuleEnabled(UUID tenantId, String moduleCode) {
        return tenantSubscriptionService.isModuleEnabled(tenantId, moduleCode);
    }

    @Override
    public boolean isFeatureEnabled(UUID tenantId, String featureCode) {
        return false;
    }
}
