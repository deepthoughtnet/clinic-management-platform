package com.deepthoughtnet.clinic.identity.service;

import java.util.Set;
import java.util.UUID;

public interface TenantModuleEntitlementService {
    boolean isModuleEnabled(UUID tenantId, String moduleKey);

    void requireModuleEnabled(UUID tenantId, String moduleKey);

    Set<String> listEnabledModules(UUID tenantId);
}
