package com.deepthoughtnet.clinic.identity.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.identity.db.TenantOnboardingEntity;
import com.deepthoughtnet.clinic.identity.db.TenantOnboardingRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantOnboardingServiceTest {

    @Test
    void onboardingStatusIsCreatedWhenMissing() {
        TenantOnboardingRepository repository = mock(TenantOnboardingRepository.class);
        UUID tenantId = UUID.randomUUID();
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0, TenantOnboardingEntity.class));

        TenantOnboardingService service = new TenantOnboardingService(repository);
        assertFalse(service.getStatus(tenantId).completed());
    }

    @Test
    void onboardingCanBeMarkedCompleteAndSkipped() {
        TenantOnboardingRepository repository = mock(TenantOnboardingRepository.class);
        UUID tenantId = UUID.randomUUID();
        TenantOnboardingEntity entity = TenantOnboardingEntity.create(tenantId, false);
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(entity));

        TenantOnboardingService service = new TenantOnboardingService(repository);

        assertTrue(service.markCompleted(tenantId).completed());
        assertTrue(service.markSkipped(tenantId).skipped());
    }
}
