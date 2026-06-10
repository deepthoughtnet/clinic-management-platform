package com.deepthoughtnet.clinic.api.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.appointment.dto.AppointmentPriorityRequest;
import com.deepthoughtnet.clinic.api.appointment.dto.AppointmentRequest;
import com.deepthoughtnet.clinic.api.appointment.dto.AppointmentRescheduleRequest;
import com.deepthoughtnet.clinic.api.appointment.dto.AppointmentStatusRequest;
import com.deepthoughtnet.clinic.api.appointment.dto.QueueReorderRequest;
import com.deepthoughtnet.clinic.api.appointment.dto.WalkInAppointmentRequest;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AppointmentControllerSecurityTest {

    @Test
    void appointmentMutationEndpointsRemainRestrictedToAppointmentManage() throws Exception {
        Method create = AppointmentController.class.getMethod("create", AppointmentRequest.class);
        Method createWalkIn = AppointmentController.class.getMethod("createWalkIn", WalkInAppointmentRequest.class);
        Method updateStatus = AppointmentController.class.getMethod("updateStatus", UUID.class, AppointmentStatusRequest.class);
        Method updatePriority = AppointmentController.class.getMethod("updatePriority", UUID.class, AppointmentPriorityRequest.class);
        Method reschedule = AppointmentController.class.getMethod("reschedule", UUID.class, AppointmentRescheduleRequest.class);
        Method reorderQueue = AppointmentController.class.getMethod("reorderQueue", QueueReorderRequest.class, UUID.class);

        assertThat(create.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('appointment.manage')");
        assertThat(createWalkIn.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('appointment.manage')");
        assertThat(updateStatus.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('appointment.manage')");
        assertThat(updatePriority.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('appointment.manage')");
        assertThat(reschedule.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('appointment.manage')");
        assertThat(reorderQueue.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('appointment.manage')");
    }

    @Test
    void readEndpointsStayOpenToAppointmentReadOrManageAndDoctorsKeepConsultationStartGated() throws Exception {
        Method search = AppointmentController.class.getMethod("search", UUID.class, UUID.class, LocalDate.class, String.class, String.class);
        Method get = AppointmentController.class.getMethod("get", UUID.class);
        Method today = AppointmentController.class.getMethod("today");
        Method startConsultation = AppointmentController.class.getMethod("startConsultation", UUID.class);

        assertThat(search.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@permissionChecker.hasPermission('appointment.read') or @permissionChecker.hasPermission('appointment.manage')");
        assertThat(get.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@permissionChecker.hasPermission('appointment.read') or @permissionChecker.hasPermission('appointment.manage')");
        assertThat(today.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@permissionChecker.hasPermission('appointment.read') or @permissionChecker.hasPermission('appointment.manage')");
        assertThat(startConsultation.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@permissionChecker.hasPermission('consultation.create') and @permissionChecker.hasPermission('appointment.manage') and @permissionChecker.hasRole('DOCTOR')");
    }
}
