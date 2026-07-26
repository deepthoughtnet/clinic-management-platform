package com.deepthoughtnet.clinic.api.module.runtime;

import java.util.UUID;

public interface TenantRuntimeEntitlementProvider {
    void requireTenantActive(UUID tenantId);

    void requireModuleEnabled(UUID tenantId, String moduleCode);

    boolean isModuleEnabled(UUID tenantId, String moduleCode);

    boolean isFeatureEnabled(UUID tenantId, String featureCode);
}
