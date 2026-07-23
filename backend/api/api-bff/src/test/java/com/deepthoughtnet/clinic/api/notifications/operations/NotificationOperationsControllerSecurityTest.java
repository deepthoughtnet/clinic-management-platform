package com.deepthoughtnet.clinic.api.notifications.operations;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class NotificationOperationsControllerSecurityTest {
    @Test
    void summaryEndpointIsReadOnly() throws Exception {
        Method method = NotificationOperationsController.class.getMethod("summary", com.deepthoughtnet.clinic.api.notifications.operations.dto.NotificationOperationsDtos.NotificationOperationsQuery.class);
        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .contains("notification.read")
                .contains("PLATFORM_ADMIN")
                .contains("AUDITOR");
    }

    @Test
    void retryEndpointRequiresRetryPermissionOrAdminRole() throws Exception {
        Method method = NotificationOperationsController.class.getMethod("retry", java.util.UUID.class, com.deepthoughtnet.clinic.api.notifications.operations.dto.NotificationOperationsDtos.NotificationOperationsRetryRequest.class);
        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .contains("notification.retry")
                .contains("PLATFORM_ADMIN")
                .contains("TENANT_ADMIN");
    }
}
