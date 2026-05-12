package com.deepthoughtnet.clinic.carepilot.featureflag.service;

import com.deepthoughtnet.clinic.carepilot.featureflag.service.model.FeatureFlagRecord;
import com.deepthoughtnet.clinic.identity.service.TenantSubscriptionService;
import com.deepthoughtnet.clinic.platform.core.module.ModuleKeys;
import com.deepthoughtnet.clinic.platform.core.module.SaasModuleCode;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Reads tenant entitlement state to determine whether CarePilot is enabled.
 */
@Service
public class FeatureFlagService {
    private final TenantSubscriptionService tenantSubscriptionService;

    public FeatureFlagService(TenantSubscriptionService tenantSubscriptionService) {
        this.tenantSubscriptionService = tenantSubscriptionService;
    }

    /**
     * Resolves CarePilot enablement for the current tenant.
     * Dedicated CAREPILOT entitlement is evaluated first; TELE_CALLING is only
     * used as a temporary backward-compatible fallback during migration.
     */
    public FeatureFlagRecord carePilotForTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        // Prefer the dedicated CarePilot module. Keep tele-calling fallback for transition safety.
        boolean enabled = tenantSubscriptionService.isModuleEnabled(tenantId, SaasModuleCode.CAREPILOT.name())
                || tenantSubscriptionService.isModuleEnabled(tenantId, ModuleKeys.TELE_CALLING);
        return new FeatureFlagRecord(tenantId, enabled, "tenant-module-entitlement");
    }
}
