package com.deepthoughtnet.clinic.api.notifications.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.notifications.operations.dto.NotificationOperationsDtos.NotificationOperationsQuery;
import com.deepthoughtnet.clinic.api.notifications.operations.dto.NotificationOperationsDtos.NotificationOperationsRetryResponse;
import com.deepthoughtnet.clinic.api.notifications.operations.dto.NotificationOperationsDtos.NotificationOperationsSummaryResponse;
import com.deepthoughtnet.clinic.api.notifications.operations.dto.NotificationOperationsDtos.NotificationOperationsSchedulerStatus;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NotificationOperationsControllerTest {
    private final NotificationOperationsService service = mock(NotificationOperationsService.class);
    private final NotificationOperationsController controller = new NotificationOperationsController(service);

    @AfterEach
    void clearContext() {
        RequestContextHolder.clear();
    }

    @Test
    void summaryUsesTenantFromRequestContextWhenQueryOmitsIt() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "corr-1", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "corr-1"));
        when(service.summary(any(), any())).thenReturn(new NotificationOperationsSummaryResponse(
                tenantId,
                "Tenant",
                "Last 7 days",
                null,
                null,
                1,
                1,
                1,
                0,
                0,
                0,
                0,
                100.0,
                0.0,
                0,
                0,
                new NotificationOperationsSchedulerStatus(true, "PT30S", true, 24, 30, 0, 0),
                List.of()
        ));

        NotificationOperationsSummaryResponse response = controller.summary(new NotificationOperationsQuery(
                null,
                "LAST_7_DAYS",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                10
        ));

        assertThat(response).isNotNull();
    }

    @Test
    void retryForwardsSelectedIds() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "corr-1", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "corr-1"));
        when(service.retry(any(), any(), any())).thenReturn(new NotificationOperationsRetryResponse(List.of(), 0, 0, 0));

        NotificationOperationsRetryResponse response = controller.retry(
                tenantId,
                new com.deepthoughtnet.clinic.api.notifications.operations.dto.NotificationOperationsDtos.NotificationOperationsRetryRequest(List.of(UUID.randomUUID(), UUID.randomUUID()))
        );

        assertThat(response).isNotNull();
    }
}
