package com.deepthoughtnet.clinic.api.module.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.commercial.entitlement.CommercialEffectiveEntitlementService;
import com.deepthoughtnet.clinic.platform.core.config.CommercialRuntimeProperties;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CommercialRuntimeEntitlementProviderTest {

    @Test
    void routesToLegacyProviderWhenRuntimeDisabled() {
        LegacyTenantRuntimeEntitlementProvider legacyProvider = mock(LegacyTenantRuntimeEntitlementProvider.class);
        CommercialTenantRuntimeEntitlementProvider commercialProvider = mock(CommercialTenantRuntimeEntitlementProvider.class);
        CommercialEffectiveEntitlementService effectiveEntitlementService = mock(CommercialEffectiveEntitlementService.class);
        CommercialRuntimeProperties properties = new CommercialRuntimeProperties();

        UUID tenantId = UUID.randomUUID();
        when(legacyProvider.isModuleEnabled(tenantId, "CAREPILOT")).thenReturn(true);
        when(legacyProvider.isFeatureEnabled(tenantId, "AI_COPILOT_WRITE")).thenReturn(false);
        when(commercialProvider.isModuleEnabled(tenantId, "CAREPILOT")).thenReturn(false);
        when(commercialProvider.isFeatureEnabled(tenantId, "AI_COPILOT_WRITE")).thenReturn(true);

        FeatureFlagTenantRuntimeEntitlementProvider provider = new FeatureFlagTenantRuntimeEntitlementProvider(
                legacyProvider,
                commercialProvider,
                effectiveEntitlementService,
                properties
        );

        assertThat(provider.isModuleEnabled(tenantId, "CAREPILOT")).isTrue();
        assertThat(provider.isFeatureEnabled(tenantId, "AI_COPILOT_WRITE")).isFalse();
        provider.requireModuleEnabled(tenantId, "CAREPILOT");

        verify(legacyProvider, times(3)).isModuleEnabled(tenantId, "CAREPILOT");
        verify(legacyProvider, times(2)).isFeatureEnabled(tenantId, "AI_COPILOT_WRITE");
        verify(legacyProvider, times(1)).requireModuleEnabled(tenantId, "CAREPILOT");
        verify(commercialProvider, times(2)).isModuleEnabled(tenantId, "CAREPILOT");
        verify(commercialProvider, times(1)).isFeatureEnabled(tenantId, "AI_COPILOT_WRITE");
        verifyNoMoreInteractions(legacyProvider, commercialProvider, effectiveEntitlementService);
    }

    @Test
    void routesToCommercialProviderOnlyForAllowlistedTenantsWhenRuntimeEnabled() {
        LegacyTenantRuntimeEntitlementProvider legacyProvider = mock(LegacyTenantRuntimeEntitlementProvider.class);
        CommercialTenantRuntimeEntitlementProvider commercialProvider = mock(CommercialTenantRuntimeEntitlementProvider.class);
        CommercialEffectiveEntitlementService effectiveEntitlementService = mock(CommercialEffectiveEntitlementService.class);
        CommercialRuntimeProperties properties = new CommercialRuntimeProperties();
        properties.setEnabled(true);

        UUID allowlistedTenant = UUID.randomUUID();
        UUID legacyTenant = UUID.randomUUID();
        properties.setTenantAllowlist(Set.of(allowlistedTenant.toString()));

        when(legacyProvider.isModuleEnabled(allowlistedTenant, "LABORATORY")).thenReturn(false);
        when(commercialProvider.isModuleEnabled(allowlistedTenant, "LABORATORY")).thenReturn(true);
        when(legacyProvider.isModuleEnabled(legacyTenant, "LABORATORY")).thenReturn(true);
        when(commercialProvider.isModuleEnabled(legacyTenant, "LABORATORY")).thenReturn(false);

        FeatureFlagTenantRuntimeEntitlementProvider provider = new FeatureFlagTenantRuntimeEntitlementProvider(
                legacyProvider,
                commercialProvider,
                effectiveEntitlementService,
                properties
        );

        assertThat(provider.isModuleEnabled(allowlistedTenant, "LABORATORY")).isTrue();
        assertThat(provider.isModuleEnabled(legacyTenant, "LABORATORY")).isTrue();

        verify(legacyProvider, times(1)).isModuleEnabled(allowlistedTenant, "LABORATORY");
        verify(commercialProvider, times(2)).isModuleEnabled(allowlistedTenant, "LABORATORY");
        verify(legacyProvider, times(2)).isModuleEnabled(legacyTenant, "LABORATORY");
        verify(commercialProvider, times(1)).isModuleEnabled(legacyTenant, "LABORATORY");
        verifyNoMoreInteractions(legacyProvider, commercialProvider, effectiveEntitlementService);
    }
}
