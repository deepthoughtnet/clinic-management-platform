package com.deepthoughtnet.clinic.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.identity.db.TenantModuleRepository;
import com.deepthoughtnet.clinic.identity.db.TenantRepository;
import com.deepthoughtnet.clinic.identity.db.TenantSubscriptionRepository;
import com.deepthoughtnet.clinic.platform.core.module.ModuleKeys;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantSubscriptionServiceTest {
    @Test
    void carePilotChecksDedicatedFlagFirstThenFallsBackToTeleCalling() {
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantSubscriptionRepository subscriptionRepository = mock(TenantSubscriptionRepository.class);
        TenantModuleRepository moduleRepository = mock(TenantModuleRepository.class);
        TenantModuleEntitlementService entitlementService = mock(TenantModuleEntitlementService.class);

        UUID tenantId = UUID.randomUUID();
        when(moduleRepository.findByTenantIdAndModuleCode(tenantId, "CAREPILOT")).thenReturn(Optional.empty());
        when(entitlementService.isModuleEnabled(tenantId, ModuleKeys.CAREPILOT)).thenReturn(false);
        when(entitlementService.isModuleEnabled(tenantId, ModuleKeys.TELE_CALLING)).thenReturn(true);

        TenantSubscriptionService service = new TenantSubscriptionService(
                tenantRepository,
                subscriptionRepository,
                moduleRepository,
                entitlementService
        );

        assertThat(service.isModuleEnabled(tenantId, "CAREPILOT")).isTrue();
        verify(entitlementService).isModuleEnabled(tenantId, ModuleKeys.CAREPILOT);
        verify(entitlementService).isModuleEnabled(tenantId, ModuleKeys.TELE_CALLING);
    }

    @Test
    void carePilotReturnsTrueWhenDedicatedFlagIsEnabled() {
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantSubscriptionRepository subscriptionRepository = mock(TenantSubscriptionRepository.class);
        TenantModuleRepository moduleRepository = mock(TenantModuleRepository.class);
        TenantModuleEntitlementService entitlementService = mock(TenantModuleEntitlementService.class);

        UUID tenantId = UUID.randomUUID();
        when(moduleRepository.findByTenantIdAndModuleCode(tenantId, "CAREPILOT")).thenReturn(Optional.empty());
        when(entitlementService.isModuleEnabled(tenantId, ModuleKeys.CAREPILOT)).thenReturn(true);

        TenantSubscriptionService service = new TenantSubscriptionService(
                tenantRepository,
                subscriptionRepository,
                moduleRepository,
                entitlementService
        );

        assertThat(service.isModuleEnabled(tenantId, "CAREPILOT")).isTrue();
    }

    @Test
    void carePilotReturnsFalseWhenDedicatedAndFallbackFlagsAreDisabled() {
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantSubscriptionRepository subscriptionRepository = mock(TenantSubscriptionRepository.class);
        TenantModuleRepository moduleRepository = mock(TenantModuleRepository.class);
        TenantModuleEntitlementService entitlementService = mock(TenantModuleEntitlementService.class);

        UUID tenantId = UUID.randomUUID();
        when(moduleRepository.findByTenantIdAndModuleCode(tenantId, "CAREPILOT")).thenReturn(Optional.empty());
        when(entitlementService.isModuleEnabled(tenantId, ModuleKeys.CAREPILOT)).thenReturn(false);
        when(entitlementService.isModuleEnabled(tenantId, ModuleKeys.TELE_CALLING)).thenReturn(false);

        TenantSubscriptionService service = new TenantSubscriptionService(
                tenantRepository,
                subscriptionRepository,
                moduleRepository,
                entitlementService
        );

        assertThat(service.isModuleEnabled(tenantId, "CAREPILOT")).isFalse();
    }
}
