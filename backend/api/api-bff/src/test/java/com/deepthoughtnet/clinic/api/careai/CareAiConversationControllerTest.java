package com.deepthoughtnet.clinic.api.careai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.ai.careai.persistence.CareAiConversationPersistenceService;
import com.deepthoughtnet.clinic.ai.careai.persistence.db.CareAiConversationEntity;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.security.Permissions;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class CareAiConversationControllerTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void listUsesTenantFromRequestContext() {
        UUID tenantId = UUID.randomUUID();
        CareAiConversationPersistenceService service = mock(CareAiConversationPersistenceService.class);
        when(service.listConversations(tenantId)).thenReturn(List.of(
                CareAiConversationEntity.create(tenantId, "PATIENT_PORTAL_CHAT", null, null, "session-1")
        ));
        RequestContextHolder.set(new RequestContext(new TenantId(tenantId), UUID.randomUUID(), "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "corr"));

        CareAiConversationController controller = new CareAiConversationController(service);
        var rows = controller.list();

        verify(service).listConversations(tenantId);
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().tenantId()).isEqualTo(tenantId);
    }

    @Test
    void listEndpointRequiresReceptionOperateOrViewPermission() throws Exception {
        Method method = CareAiConversationController.class.getMethod("list");
        String guard = method.getAnnotation(PreAuthorize.class).value();
        assertThat(guard).contains(Permissions.ENGAGE_RECEPTION_OPERATE).contains(Permissions.ENGAGE_VIEW);
    }

    @Test
    void messagesEndpointRequiresReceptionOperateOrViewPermission() throws Exception {
        Method method = CareAiConversationController.class.getMethod("messages", UUID.class);
        String guard = method.getAnnotation(PreAuthorize.class).value();
        assertThat(guard).contains(Permissions.ENGAGE_RECEPTION_OPERATE).contains(Permissions.ENGAGE_VIEW);
    }

    @Test
    void activeUsesTenantFromRequestContext() {
        UUID tenantId = UUID.randomUUID();
        CareAiConversationPersistenceService service = mock(CareAiConversationPersistenceService.class);
        when(service.listActiveConversations(tenantId)).thenReturn(List.of(
                CareAiConversationEntity.create(tenantId, "PATIENT_PORTAL_VOICE", null, null, "session-active")
        ));
        RequestContextHolder.set(new RequestContext(new TenantId(tenantId), UUID.randomUUID(), "sub", Set.of("RECEPTIONIST"), "RECEPTIONIST", "corr"));

        CareAiConversationController controller = new CareAiConversationController(service);
        var rows = controller.active(null);

        verify(service).listActiveConversations(tenantId);
        assertThat(rows).hasSize(1);
    }
}
