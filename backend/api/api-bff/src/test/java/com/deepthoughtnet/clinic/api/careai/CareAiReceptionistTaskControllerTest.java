package com.deepthoughtnet.clinic.api.careai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.ai.careai.task.CareAiReceptionistTaskService;
import com.deepthoughtnet.clinic.ai.careai.task.db.CareAiReceptionistTaskEntity;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class CareAiReceptionistTaskControllerTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void listUsesTenantScopedServiceCall() {
        UUID tenantId = UUID.randomUUID();
        CareAiReceptionistTaskService taskService = mock(CareAiReceptionistTaskService.class);
        when(taskService.listTasks(org.mockito.Mockito.eq(tenantId), any())).thenReturn(List.of(
                CareAiReceptionistTaskEntity.create(
                        tenantId,
                        null,
                        null,
                        null,
                        null,
                        null,
                        com.deepthoughtnet.clinic.ai.careai.task.CareAiReceptionistTaskType.HUMAN_HANDOFF,
                        com.deepthoughtnet.clinic.ai.careai.task.CareAiReceptionistTaskPriority.MEDIUM,
                        "PATIENT_PORTAL_CHAT",
                        "requested-receptionist",
                        "talk to receptionist",
                        null,
                        null,
                        java.time.OffsetDateTime.now().plusMinutes(15),
                        "{}"
                )
        ));
        RequestContextHolder.set(new RequestContext(new TenantId(tenantId), UUID.randomUUID(), "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "corr"));

        CareAiReceptionistTaskController controller = new CareAiReceptionistTaskController(taskService);
        var rows = controller.list(null, null, null, false, false, false, null);

        verify(taskService).listTasks(org.mockito.Mockito.eq(tenantId), any());
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().tenantId()).isEqualTo(tenantId);
    }

    @Test
    void listEndpointAllowsReceptionistAuditorAndPlatformSupport() throws Exception {
        Method method = CareAiReceptionistTaskController.class.getMethod("list", com.deepthoughtnet.clinic.ai.careai.task.CareAiReceptionistTaskStatus.class, com.deepthoughtnet.clinic.ai.careai.task.CareAiReceptionistTaskType.class, com.deepthoughtnet.clinic.ai.careai.task.CareAiReceptionistTaskPriority.class, boolean.class, boolean.class, boolean.class, UUID.class);
        String guard = method.getAnnotation(PreAuthorize.class).value();
        assertThat(guard).contains("CLINIC_ADMIN").contains("RECEPTIONIST").contains("AUDITOR").contains("PLATFORM_TENANT_SUPPORT");
    }

    @Test
    void resolveEndpointAllowsReceptionist() throws Exception {
        Method method = CareAiReceptionistTaskController.class.getMethod("resolve", UUID.class, CareAiReceptionistTaskController.TaskMutationRequest.class);
        String guard = method.getAnnotation(PreAuthorize.class).value();
        assertThat(guard).contains("CLINIC_ADMIN").contains("RECEPTIONIST");
    }

    @Test
    void appointmentHandoffsEndpointForcesAppointmentHandoffType() {
        UUID tenantId = UUID.randomUUID();
        CareAiReceptionistTaskService taskService = mock(CareAiReceptionistTaskService.class);
        when(taskService.listTasks(org.mockito.Mockito.eq(tenantId), any())).thenReturn(List.of());
        RequestContextHolder.set(new RequestContext(new TenantId(tenantId), UUID.randomUUID(), "sub", Set.of("RECEPTIONIST"), "RECEPTIONIST", "corr"));

        CareAiReceptionistTaskController controller = new CareAiReceptionistTaskController(taskService);
        controller.appointmentHandoffs(null, null, false, false, false, null);

        verify(taskService).listTasks(org.mockito.Mockito.eq(tenantId), argThat(filter ->
                filter != null && filter.type() == com.deepthoughtnet.clinic.ai.careai.task.CareAiReceptionistTaskType.APPOINTMENT_HANDOFF
        ));
    }
}
