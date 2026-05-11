package com.deepthoughtnet.clinic.api.reports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.dashboard.dto.ClinicDashboardResponse;
import com.deepthoughtnet.clinic.api.security.DoctorAssignmentSecurityService;
import com.deepthoughtnet.clinic.api.security.PermissionChecker;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportsControllerRoleAccessTest {
    @Mock private ReportingFacade reportingFacade;
    @Mock private DoctorAssignmentSecurityService doctorAssignmentSecurityService;
    @Mock private PermissionChecker permissionChecker;

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void platformAdminWithoutTenantGetsPlatformPayloadOnly() {
        ReportsController controller = new ReportsController(reportingFacade, doctorAssignmentSecurityService, permissionChecker);
        RequestContextHolder.set(new RequestContext(null, null, null, Set.of("PLATFORM_ADMIN"), null, "cid"));
        ClinicDashboardResponse platformPayload = new ClinicDashboardResponse(
                LocalDate.of(2026, 5, 11),
                LocalDate.of(2026, 5, 11),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of()
        );

        when(permissionChecker.hasRole(anyString())).thenAnswer(invocation -> "PLATFORM_ADMIN".equals(invocation.getArgument(0)));
        when(reportingFacade.platformDashboard(LocalDate.of(2026, 5, 11), LocalDate.of(2026, 5, 11))).thenReturn(platformPayload);

        ClinicDashboardResponse response = controller.clinicDashboard(LocalDate.of(2026, 5, 11), null, null, null);

        assertEquals(null, response.tenantId());
        verifyNoInteractions(doctorAssignmentSecurityService);
    }
}
