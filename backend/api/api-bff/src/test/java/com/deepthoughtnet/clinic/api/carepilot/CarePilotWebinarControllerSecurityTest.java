package com.deepthoughtnet.clinic.api.carepilot;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class CarePilotWebinarControllerSecurityTest {

    @Test
    void endpointGuardsUseGranularPermissions() throws Exception {
        Method list = CarePilotWebinarController.class.getMethod(
                "list",
                com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarStatus.class,
                com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarType.class,
                java.time.LocalDate.class,
                java.time.LocalDate.class,
                Boolean.class,
                Boolean.class,
                int.class,
                int.class
        );
        Method get = CarePilotWebinarController.class.getMethod("get", java.util.UUID.class);
        Method create = CarePilotWebinarController.class.getMethod("create", com.deepthoughtnet.clinic.api.carepilot.dto.WebinarDtos.WebinarUpsertRequest.class);
        Method update = CarePilotWebinarController.class.getMethod("update", java.util.UUID.class, com.deepthoughtnet.clinic.api.carepilot.dto.WebinarDtos.WebinarUpsertRequest.class);
        Method status = CarePilotWebinarController.class.getMethod("updateStatus", java.util.UUID.class, com.deepthoughtnet.clinic.api.carepilot.dto.WebinarDtos.WebinarStatusUpdateRequest.class);
        Method registrations = CarePilotWebinarController.class.getMethod("registrations", java.util.UUID.class, int.class, int.class);
        Method register = CarePilotWebinarController.class.getMethod("register", java.util.UUID.class, com.deepthoughtnet.clinic.api.carepilot.dto.WebinarDtos.WebinarRegistrationRequest.class);
        Method attendance = CarePilotWebinarController.class.getMethod("attendance", java.util.UUID.class, com.deepthoughtnet.clinic.api.carepilot.dto.WebinarDtos.WebinarAttendanceRequest.class);
        Method analytics = CarePilotWebinarController.class.getMethod("analytics");

        assertThat(list.getAnnotation(PreAuthorize.class).value()).contains("engage.webinar.view");
        assertThat(get.getAnnotation(PreAuthorize.class).value()).contains("engage.webinar.view");
        assertThat(create.getAnnotation(PreAuthorize.class).value()).contains("engage.webinar.create");
        assertThat(update.getAnnotation(PreAuthorize.class).value()).contains("engage.webinar.edit");
        assertThat(status.getAnnotation(PreAuthorize.class).value()).contains("engage.webinar.publish");
        assertThat(registrations.getAnnotation(PreAuthorize.class).value()).contains("engage.webinar.manage.registrations");
        assertThat(register.getAnnotation(PreAuthorize.class).value()).contains("engage.webinar.manage.registrations");
        assertThat(attendance.getAnnotation(PreAuthorize.class).value()).contains("engage.webinar.record.attendance");
        assertThat(analytics.getAnnotation(PreAuthorize.class).value()).contains("engage.webinar.view.analytics");
    }
}
