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
import org.mockito.ArgumentCaptor;

class TenantUserManagementServiceTest {

    private final UUID tenantId = UUID.randomUUID();

    @Test
    void tenantUserServiceAcceptsNewCanonicalRoles() {
        assertDoesNotThrow(() -> createService().createOrInvite(command("PHARMACIST")));
        assertDoesNotThrow(() -> createService().createOrInvite(command("PHARMACY_INVENTORY_MANAGER")));
        assertDoesNotThrow(() -> createService().createOrInvite(command("PHARMACY_POS_USER")));
        assertDoesNotThrow(() -> createService().createOrInvite(command("LAB_APPROVER")));
        assertDoesNotThrow(() -> createService().createOrInvite(command("LAB_FRONT_DESK")));
    }

    @Test
    void createdMembershipKeepsRequestedRole() {
        TenantUserRecord record = createService().createOrInvite(command("PHARMACY_POS_USER"));
        assertEquals("PHARMACY_POS_USER", record.membershipRole());
    }

    @Test
    void createOrInvitePersistsUsernameEmployeeCodeMobileAndDepartment() {
        AppUserRepository appUserRepository = mock(AppUserRepository.class);
        TenantMembershipRepository membershipRepository = mock(TenantMembershipRepository.class);
        KeycloakAdminProvisioner keycloakAdminProvisioner = mock(KeycloakAdminProvisioner.class);

        when(keycloakAdminProvisioner.createOrGetTenantUserId(any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn("kc-sub");
        when(appUserRepository.findByTenantIdAndKeycloakSub(any(), any())).thenReturn(Optional.empty());
        when(appUserRepository.findByTenantIdAndEmailIgnoreCase(any(), any())).thenReturn(Optional.empty());
        when(appUserRepository.findByTenantIdAndUsernameIgnoreCase(any(), any())).thenReturn(Optional.empty());
        when(appUserRepository.findByTenantIdAndEmployeeCodeIgnoreCase(any(), any())).thenReturn(Optional.empty());
        when(appUserRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0, AppUserEntity.class));
        when(membershipRepository.findByTenantIdAndAppUserId(any(), any())).thenReturn(Optional.empty());
        when(membershipRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0, TenantMembershipEntity.class));

        TenantUserManagementService service = new TenantUserManagementService(appUserRepository, membershipRepository, keycloakAdminProvisioner);
        ArgumentCaptor<AppUserEntity> captor = ArgumentCaptor.forClass(AppUserEntity.class);

        service.createOrInvite(new CreateTenantUserCommand(
                tenantId,
                "user@example.com",
                "reception01",
                "User One",
                "RECEPTIONIST",
                "Temp@1234",
                "EMP-001",
                "9876543210",
                "Reception"
        ));

        org.mockito.Mockito.verify(appUserRepository).save(captor.capture());
        assertEquals("reception01", captor.getValue().getUsername());
        assertEquals("EMP-001", captor.getValue().getEmployeeCode());
        assertEquals("9876543210", captor.getValue().getMobile());
        assertEquals("Reception", captor.getValue().getDepartment());
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
        when(appUserRepository.findByTenantIdAndEmailIgnoreCase(any(), any()))
                .thenReturn(Optional.empty());
        when(appUserRepository.findByTenantIdAndUsernameIgnoreCase(any(), any()))
                .thenReturn(Optional.empty());
        when(appUserRepository.findByTenantIdAndEmployeeCodeIgnoreCase(any(), any()))
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
