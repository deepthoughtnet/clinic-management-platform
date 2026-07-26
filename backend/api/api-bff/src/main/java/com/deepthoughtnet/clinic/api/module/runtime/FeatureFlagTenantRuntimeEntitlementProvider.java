package com.deepthoughtnet.clinic.api.module.runtime;

import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementService;
import com.deepthoughtnet.clinic.platform.core.config.CommercialRuntimeProperties;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class FeatureFlagTenantRuntimeEntitlementProvider implements TenantRuntimeEntitlementProvider {
    private static final Logger log = LoggerFactory.getLogger(FeatureFlagTenantRuntimeEntitlementProvider.class);

    private final LegacyTenantRuntimeEntitlementProvider legacyProvider;
    private final CommercialTenantRuntimeEntitlementProvider commercialProvider;
    private final CommercialEffectiveEntitlementService commercialEffectiveEntitlementService;
    private final CommercialRuntimeProperties runtimeProperties;

    public FeatureFlagTenantRuntimeEntitlementProvider(
            LegacyTenantRuntimeEntitlementProvider legacyProvider,
            CommercialTenantRuntimeEntitlementProvider commercialProvider,
            CommercialEffectiveEntitlementService commercialEffectiveEntitlementService,
            CommercialRuntimeProperties runtimeProperties
    ) {
        this.legacyProvider = legacyProvider;
        this.commercialProvider = commercialProvider;
        this.commercialEffectiveEntitlementService = commercialEffectiveEntitlementService;
        this.runtimeProperties = runtimeProperties;
    }

    @Override
    public void requireTenantActive(UUID tenantId) {
        selectProvider(tenantId).requireTenantActive(tenantId);
    }

    @Override
    public void requireModuleEnabled(UUID tenantId, String moduleCode) {
        boolean legacy = legacyProvider.isModuleEnabled(tenantId, moduleCode);
        TenantRuntimeEntitlementProvider provider = selectProvider(tenantId);
        boolean commercial = commercialProvider.isModuleEnabled(tenantId, moduleCode);
        if (!runtimeProperties.isEnabled() && runtimeProperties.isShadowCompareEnabled() && legacy != commercial) {
            logShadowMismatch(tenantId, moduleCode, legacy, commercial);
        }
        provider.requireModuleEnabled(tenantId, moduleCode);
    }

    @Override
    public boolean isModuleEnabled(UUID tenantId, String moduleCode) {
        boolean legacy = legacyProvider.isModuleEnabled(tenantId, moduleCode);
        boolean commercial = commercialProvider.isModuleEnabled(tenantId, moduleCode);
        if (!runtimeProperties.isEnabled() && runtimeProperties.isShadowCompareEnabled() && legacy != commercial) {
            logShadowMismatch(tenantId, moduleCode, legacy, commercial);
        }
        return selectProvider(tenantId).isModuleEnabled(tenantId, moduleCode);
    }

    @Override
    public boolean isFeatureEnabled(UUID tenantId, String featureCode) {
        boolean legacy = legacyProvider.isFeatureEnabled(tenantId, featureCode);
        boolean commercial = commercialProvider.isFeatureEnabled(tenantId, featureCode);
        if (!runtimeProperties.isEnabled() && runtimeProperties.isShadowCompareEnabled() && legacy != commercial) {
            logShadowMismatch(tenantId, featureCode, legacy, commercial);
        }
        return selectProvider(tenantId).isFeatureEnabled(tenantId, featureCode);
    }

    private TenantRuntimeEntitlementProvider selectProvider(UUID tenantId) {
        if (!runtimeProperties.isEnabled()) {
            return legacyProvider;
        }
        if (runtimeProperties.getTenantAllowlist() != null && !runtimeProperties.getTenantAllowlist().isEmpty() && !runtimeProperties.getTenantAllowlist().contains(String.valueOf(tenantId))) {
            return legacyProvider;
        }
        return commercialProvider;
    }

    private void logShadowMismatch(UUID tenantId, String key, boolean legacy, boolean commercial) {
        log.info("commercial.runtime.shadow-mismatch tenantId={} key={} legacy={} commercial={} sourceHash={} contentHash={}",
                tenantId,
                key,
                legacy,
                commercial,
                commercialEffectiveEntitlementService.getCurrentSnapshot(tenantId).sourceHash(),
                commercialEffectiveEntitlementService.getCurrentSnapshot(tenantId).contentHash());
    }
}
