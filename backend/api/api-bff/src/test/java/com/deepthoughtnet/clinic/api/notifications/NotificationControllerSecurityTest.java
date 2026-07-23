package com.deepthoughtnet.clinic.api.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class NotificationControllerSecurityTest {

    @Test
    void listEndpointRequiresNotificationReadOnly() throws Exception {
        Method list = NotificationController.class.getMethod(
                "list",
                String.class,
                String.class,
                String.class,
                java.util.UUID.class,
                java.time.OffsetDateTime.class,
                java.time.OffsetDateTime.class
        );

        assertThat(list.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@permissionChecker.hasPermission('notification.read')");
    }

    @Test
    void groupedEndpointUsesTheSameReadPermission() throws Exception {
        Method grouped = NotificationController.class.getMethod(
                "grouped",
                String.class,
                String.class,
                String.class,
                java.util.UUID.class,
                java.time.OffsetDateTime.class,
                java.time.OffsetDateTime.class
        );

        assertThat(grouped.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@permissionChecker.hasPermission('notification.read')");
    }

    @Test
    void patientScopedListRemainsPatientRead() throws Exception {
        Method listByPatient = NotificationController.class.getMethod("listByPatient", java.util.UUID.class);

        assertThat(listByPatient.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@permissionChecker.hasPermission('patient.read')");
    }
}
