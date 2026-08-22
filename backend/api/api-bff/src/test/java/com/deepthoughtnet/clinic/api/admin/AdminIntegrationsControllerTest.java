package com.deepthoughtnet.clinic.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.admin.dto.AdminIntegrationsDtos.IntegrationStatusRow;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AdminIntegrationsControllerTest {
    private final AdminIntegrationsStatusService service = mock(AdminIntegrationsStatusService.class);
    private final AdminIntegrationsController controller = new AdminIntegrationsController(service);

    @AfterEach
    void clearContext() {
        RequestContextHolder.clear();
    }

    @Test
    void clinicAdminReceivesTenantFriendlyGuidanceWithoutTechnicalDetails() {
        UUID tenantId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "corr-1", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "corr-1"));
        when(service.status(any(), anyBoolean())).thenReturn(List.<IntegrationStatusRow>of());

        var response = controller.status();

        assertThat(response.rows()).isEmpty();
        verify(service).status(tenantId, false);
    }

    @Test
    void platformAdminCanRequestTechnicalDetails() {
        UUID tenantId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), UUID.randomUUID(), "corr-1", Set.of("PLATFORM_ADMIN"), "PLATFORM_ADMIN", "corr-1"));
        when(service.status(any(), anyBoolean())).thenReturn(List.<IntegrationStatusRow>of());

        var response = controller.status();

        assertThat(response.rows()).isEmpty();
        verify(service).status(tenantId, true);
    }
}
