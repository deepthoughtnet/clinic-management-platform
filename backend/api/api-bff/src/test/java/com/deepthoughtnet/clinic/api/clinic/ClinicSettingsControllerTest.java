package com.deepthoughtnet.clinic.api.clinic;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClinicSettingsControllerTest {
    private final UUID tenantId = UUID.randomUUID();
    private TenantUserManagementService tenantUserManagementService;
    private ClinicSettingsController controller;

    @BeforeEach
    void setUp() {
        tenantUserManagementService = mock(TenantUserManagementService.class);
        controller = new ClinicSettingsController(mock(ClinicProfileService.class), tenantUserManagementService);
        RequestContextHolder.set(new RequestContext(
                TenantId.of(tenantId),
                UUID.randomUUID(),
                "admin@jfcuat.local",
                Set.of("CLINIC_ADMIN"),
                "CLINIC_ADMIN",
                "test-correlation"
        ));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void getUsersIncludesEngageManagerRole() {
        when(tenantUserManagementService.list(tenantId)).thenReturn(List.of(
                new TenantUserRecord(
                        UUID.randomUUID(),
                        tenantId,
                        "sub-1",
                        "nisha.kulkarni@jfcuat.local",
                        "nisha.kulkarni",
                        "Engage",
                        "Nisha Kulkarni",
                        "ACTIVE",
                        "ENGAGE_MANAGER",
                        "ACTIVE",
                        OffsetDateTime.parse("2026-07-18T08:00:51.58686Z"),
                        OffsetDateTime.parse("2026-07-18T08:30:50.861116Z"),
                        "EXISTING"
                )
        ));

        assertTrue(controller.getUsers().stream().anyMatch(user -> "ENGAGE_MANAGER".equals(user.membershipRole())));
    }

    @Test
    void getRolesIncludesEngageManagerAndExecutive() {
        assertTrue(controller.getRoles().stream().anyMatch(role -> "ENGAGE_MANAGER".equals(role.role())));
        assertTrue(controller.getRoles().stream().anyMatch(role -> "ENGAGE_EXECUTIVE".equals(role.role())));
    }
}
