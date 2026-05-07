package com.deepthoughtnet.clinic.api.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.CreateTenantUserCommand;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.security.Permissions;
import com.deepthoughtnet.clinic.platform.security.RolePermissionMappings;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class TenantUserManagementControllerTest {
    private final UUID tenantId = UUID.randomUUID();
    private TenantUserManagementService tenantUserManagementService;
    private TenantUserManagementController controller;

    @BeforeEach
    void setUp() {
        tenantUserManagementService = mock(TenantUserManagementService.class);
        controller = new TenantUserManagementController(tenantUserManagementService);
        RequestContextHolder.set(new RequestContext(
                TenantId.of(tenantId),
                UUID.randomUUID(),
                "clinic-admin-sub",
                Set.of(),
                "CLINIC_ADMIN",
                "test-correlation"
        ));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLINIC_ADMIN", "DOCTOR", "RECEPTIONIST", "BILLING_USER", "AUDITOR", "SERVICE_AGENT"})
    void clinicAdminCanCreateSupportedTenantRoles(String role) {
        when(tenantUserManagementService.createOrInvite(any())).thenReturn(record(role));

        controller.create(request(role, "Temp@1234"));

        ArgumentCaptor<CreateTenantUserCommand> captor = ArgumentCaptor.forClass(CreateTenantUserCommand.class);
        verify(tenantUserManagementService).createOrInvite(captor.capture());
        assertEquals(role, captor.getValue().role());
        assertEquals("Temp@1234", captor.getValue().tempPassword());
    }

    @Test
    void createUserRequestDoesNotMapTemporaryPasswordIntoRole() {
        when(tenantUserManagementService.createOrInvite(any())).thenReturn(record("RECEPTIONIST"));

        controller.create(request("RECEPTIONIST", "Temp@1234"));

        ArgumentCaptor<CreateTenantUserCommand> captor = ArgumentCaptor.forClass(CreateTenantUserCommand.class);
        verify(tenantUserManagementService).createOrInvite(captor.capture());
        assertEquals("RECEPTIONIST", captor.getValue().role());
        assertEquals("Temp@1234", captor.getValue().tempPassword());
    }

    @Test
    void legacyTempPasswordFieldIsStillAccepted() {
        when(tenantUserManagementService.createOrInvite(any())).thenReturn(record("DOCTOR"));

        controller.create(new TenantUserManagementController.CreateTenantUserRequest(
                "doctor@example.com",
                null,
                "Doctor",
                "One",
                "DOCTOR",
                "Legacy@1234",
                null,
                true
        ));

        ArgumentCaptor<CreateTenantUserCommand> captor = ArgumentCaptor.forClass(CreateTenantUserCommand.class);
        verify(tenantUserManagementService).createOrInvite(captor.capture());
        assertEquals("DOCTOR", captor.getValue().role());
        assertEquals("Legacy@1234", captor.getValue().tempPassword());
    }

    @Test
    void clinicAdminCannotCreatePlatformAdmin() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> controller.create(request("PLATFORM_ADMIN", "Temp@1234"))
        );

        assertEquals("Role not allowed for clinic admin: PLATFORM_ADMIN", ex.getMessage());
        verify(tenantUserManagementService, never()).createOrInvite(any());
    }

    @Test
    void invalidRoleReturnsCleanValidationMessage() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> controller.create(request("not-a-role", "Temp@1234"))
        );

        assertEquals("Role not allowed for clinic admin: NOT_A_ROLE", ex.getMessage());
        verify(tenantUserManagementService, never()).createOrInvite(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECEPTIONIST", "AUDITOR", "DOCTOR", "BILLING_USER"})
    void nonAdminRolesDoNotHaveUserCreationPermissions(String role) {
        Set<String> permissions = RolePermissionMappings.permissionsForRole(role);

        assertFalse(permissions.contains(Permissions.USER_MANAGE));
        assertFalse(permissions.contains(Permissions.TENANT_USERS_MANAGE));
        assertFalse(permissions.contains(Permissions.TENANT_USERS_ROLE_ASSIGN));
        assertFalse(permissions.contains(Permissions.TENANT_USERS_RESET_PASSWORD));
    }

    private TenantUserManagementController.CreateTenantUserRequest request(String role, String temporaryPassword) {
        return new TenantUserManagementController.CreateTenantUserRequest(
                "user@example.com",
                null,
                "User",
                "One",
                role,
                null,
                temporaryPassword,
                true
        );
    }

    private TenantUserRecord record(String role) {
        OffsetDateTime now = OffsetDateTime.now();
        return new TenantUserRecord(
                UUID.randomUUID(),
                tenantId,
                "kc-sub",
                "user@example.com",
                "User One",
                "ACTIVE",
                role,
                "ACTIVE",
                now,
                now,
                "KEYCLOAK_USER_READY"
        );
    }
}
