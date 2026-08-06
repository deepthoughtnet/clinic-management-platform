package com.deepthoughtnet.clinic.api.platform.discover;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipConflictException;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.ProviderPublicProfileModerationService;
import com.deepthoughtnet.clinic.identity.db.TenantEntity;
import com.deepthoughtnet.clinic.identity.db.TenantRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HealthcarePublicListingStartupReconcilerTest {

    @Test
    void reconcilesOnlyActiveTenantsOnStartup() {
        TenantRepository tenantRepository = mock(TenantRepository.class);
        HealthcarePublicListingSyncService syncService = mock(HealthcarePublicListingSyncService.class);
        ProviderPublicProfileModerationService moderationService = mock(ProviderPublicProfileModerationService.class);
        HealthcarePublicListingSyncService.HealthcarePublicListingSyncSummary summary =
                new HealthcarePublicListingSyncService.HealthcarePublicListingSyncSummary(1, 2, 3, 4, List.of());
        when(syncService.syncTenant(any(), any(), any())).thenReturn(summary);

        TenantEntity activeTenant = TenantEntity.create("active-clinic", "Active Clinic", "TRIAL");
        TenantEntity inactiveTenant = TenantEntity.create("inactive-clinic", "Inactive Clinic", "TRIAL");
        inactiveTenant.suspend();
        when(tenantRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(activeTenant, inactiveTenant));

        HealthcarePublicListingStartupReconciler reconciler = new HealthcarePublicListingStartupReconciler(syncService, tenantRepository, moderationService);
        reconciler.reconcile();

        verify(moderationService).reconcileCurrentPublishedLifecycles();
        verify(syncService).syncTenant(activeTenant.getId(), null, "startup.reconcile");
        verify(syncService, never()).syncTenant(inactiveTenant.getId(), null, "startup.reconcile");
        assertThat(activeTenant.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void reconcilerSkipsOwnershipConflictsWithoutFailingStartup() {
        TenantRepository tenantRepository = mock(TenantRepository.class);
        HealthcarePublicListingSyncService syncService = mock(HealthcarePublicListingSyncService.class);
        ProviderPublicProfileModerationService moderationService = mock(ProviderPublicProfileModerationService.class);
        TenantEntity activeTenant = TenantEntity.create("active-clinic", "Active Clinic", "TRIAL");
        when(tenantRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(activeTenant));
        when(syncService.syncTenant(activeTenant.getId(), null, "startup.reconcile"))
                .thenThrow(new ProviderOwnershipConflictException(
                        "public_profile_version_conflict",
                        "The approved submission version already exists with different profile content."
                ));

        HealthcarePublicListingStartupReconciler reconciler = new HealthcarePublicListingStartupReconciler(syncService, tenantRepository, moderationService);

        assertThatCode(reconciler::reconcile).doesNotThrowAnyException();
        verify(syncService).syncTenant(activeTenant.getId(), null, "startup.reconcile");
    }
}
