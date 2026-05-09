package com.deepthoughtnet.clinic.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.identity.db.TenantEntity;
import com.deepthoughtnet.clinic.identity.db.TenantMembershipEntity;
import com.deepthoughtnet.clinic.identity.db.TenantMembershipRepository;
import com.deepthoughtnet.clinic.identity.db.TenantRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActiveTenantMembershipServiceTest {

    @Test
    void resolvesActiveMembershipsForKeycloakSubject() {
        UUID tenantId = UUID.randomUUID();
        UUID appUserId = UUID.randomUUID();
        TenantEntity tenant = mock(TenantEntity.class);
        when(tenant.getId()).thenReturn(tenantId);
        when(tenant.getCode()).thenReturn("clinic-a");
        when(tenant.getName()).thenReturn("Clinic A");
        when(tenant.isClinicAutomationEnabled()).thenReturn(true);
        when(tenant.isClinicGenerationEnabled()).thenReturn(true);
        when(tenant.isReconciliationEnabled()).thenReturn(true);
        when(tenant.isDecisioningEnabled()).thenReturn(true);
        when(tenant.isAiCopilotEnabled()).thenReturn(true);
        when(tenant.isAgentIntakeEnabled()).thenReturn(true);
        when(tenant.isGstFilingEnabled()).thenReturn(false);
        when(tenant.isDoctorIntelligenceEnabled()).thenReturn(false);
        when(tenant.isTeleCallingEnabled()).thenReturn(false);
        TenantMembershipEntity membership = TenantMembershipEntity.create(tenantId, appUserId, "CLINIC_ADMIN");

        TenantMembershipRepository membershipRepository = mock(TenantMembershipRepository.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        when(membershipRepository.findActiveByIdentity("sub-123", "clinic.admin@clinic.local")).thenReturn(List.of(membership));
        when(tenantRepository.findAllById(anyList())).thenReturn(List.of(tenant));

        ActiveTenantMembershipService service = new ActiveTenantMembershipService(membershipRepository, tenantRepository);

        var records = service.listActiveMemberships("sub-123", "clinic.admin@clinic.local");

        assertThat(records).hasSize(1);
        assertThat(records.get(0).tenantId()).isEqualTo(tenantId);
        assertThat(records.get(0).tenantCode()).isEqualTo("clinic-a");
        assertThat(records.get(0).tenantName()).isEqualTo("Clinic A");
        assertThat(records.get(0).role()).isEqualTo("CLINIC_ADMIN");
        assertThat(records.get(0).status()).isEqualTo("ACTIVE");
    }

    @Test
    void emptyIdentityReturnsNoMemberships() {
        ActiveTenantMembershipService service = new ActiveTenantMembershipService(
                mock(TenantMembershipRepository.class),
                mock(TenantRepository.class)
        );

        assertThat(service.listActiveMemberships(null, null)).isEmpty();
    }
}
