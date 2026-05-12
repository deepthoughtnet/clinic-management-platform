package com.deepthoughtnet.clinic.carepilot.featureflag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.identity.service.TenantSubscriptionService;
import com.deepthoughtnet.clinic.platform.core.module.ModuleKeys;
import com.deepthoughtnet.clinic.platform.core.module.SaasModuleCode;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FeatureFlagServiceTest {
    @Test
    void resolvesCarePilotFlagFromTenantSubscription() {
        TenantSubscriptionService subscriptionService = mock(TenantSubscriptionService.class);
        FeatureFlagService service = new FeatureFlagService(subscriptionService);
        UUID tenantId = UUID.randomUUID();
        when(subscriptionService.isModuleEnabled(tenantId, SaasModuleCode.CAREPILOT.name())).thenReturn(true);

        var result = service.carePilotForTenant(tenantId);

        assertThat(result.carePilotEnabled()).isTrue();
        verify(subscriptionService, never()).isModuleEnabled(tenantId, ModuleKeys.TELE_CALLING);
    }

    @Test
    void fallsBackToTeleCallingDuringTransition() {
        TenantSubscriptionService subscriptionService = mock(TenantSubscriptionService.class);
        FeatureFlagService service = new FeatureFlagService(subscriptionService);
        UUID tenantId = UUID.randomUUID();
        when(subscriptionService.isModuleEnabled(tenantId, SaasModuleCode.CAREPILOT.name())).thenReturn(false);
        when(subscriptionService.isModuleEnabled(tenantId, ModuleKeys.TELE_CALLING)).thenReturn(true);

        var result = service.carePilotForTenant(tenantId);

        assertThat(result.carePilotEnabled()).isTrue();
    }
}
