package com.deepthoughtnet.clinic.identity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.identity.db.AppUserEntity;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.identity.db.TenantMembershipEntity;
import com.deepthoughtnet.clinic.identity.db.TenantMembershipRepository;
import com.deepthoughtnet.clinic.identity.service.keycloak.KeycloakAdminProvisioner;
import com.deepthoughtnet.clinic.identity.service.model.CreateTenantUserCommand;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantUserManagementServiceTest {

    private final UUID tenantId = UUID.randomUUID();

    @Test
    void tenantUserServiceAcceptsNewCanonicalRoles() {
        assertDoesNotThrow(() -> createService().createOrInvite(command("PHARMACIST")));
        assertDoesNotThrow(() -> createService().createOrInvite(command("PHARMACY_INVENTORY_MANAGER")));
        assertDoesNotThrow(() -> createService().createOrInvite(command("PHARMACY_POS_USER")));
    }

    @Test
    void createdMembershipKeepsRequestedRole() {
        TenantUserRecord record = createService().createOrInvite(command("PHARMACY_POS_USER"));
        assertEquals("PHARMACY_POS_USER", record.membershipRole());
    }

    @Test
    void unsupportedRoleIsRejected() {
        var service = createService();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.createOrInvite(command("NOT_A_ROLE")));
    }

    private TenantUserManagementService createService() {
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        TenantMembershipRepository membershipRepository = mock(TenantMembershipRepository.class);
        KeycloakAdminProvisioner keycloakAdminProvisioner = mock(KeycloakAdminProvisioner.class);

        when(keycloakAdminProvisioner.createOrGetTenantUserId(any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn("kc-sub");
        when(appUserRepository.findByTenantIdAndKeycloakSub(any(), any()))
                .thenReturn(Optional.empty());
        when(appUserRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0, AppUserEntity.class));
        when(membershipRepository.findByTenantIdAndAppUserId(any(), any()))
                .thenReturn(Optional.empty());
        when(membershipRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0, TenantMembershipEntity.class));

        return new TenantUserManagementService(appUserRepository, membershipRepository, keycloakAdminProvisioner);
    }

    private CreateTenantUserCommand command(String role) {
        return new CreateTenantUserCommand(
                tenantId,
                "user@example.com",
                "user",
                "User",
                role,
                null
        );
    }
}
