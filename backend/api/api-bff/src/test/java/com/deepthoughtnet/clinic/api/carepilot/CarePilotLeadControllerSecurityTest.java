package com.deepthoughtnet.clinic.api.carepilot;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class CarePilotLeadControllerSecurityTest {

    @Test
    void listAndReadAllowClinicAdminAuditorReceptionistAndTenantScopedPlatformAdmin() throws Exception {
        Method list = CarePilotLeadController.class.getMethod(
                "list",
                com.deepthoughtnet.clinic.carepilot.lead.model.LeadStatus.class,
                com.deepthoughtnet.clinic.carepilot.lead.model.LeadSource.class,
                java.util.UUID.class,
                com.deepthoughtnet.clinic.carepilot.lead.model.LeadPriority.class,
                String.class,
                boolean.class,
                java.time.LocalDate.class,
                java.time.LocalDate.class,
                int.class,
                int.class
        );
        Method get = CarePilotLeadController.class.getMethod("get", java.util.UUID.class);
        String listGuard = list.getAnnotation(PreAuthorize.class).value();
        String getGuard = get.getAnnotation(PreAuthorize.class).value();

        assertThat(listGuard).contains("CLINIC_ADMIN").contains("AUDITOR").contains("RECEPTIONIST").contains("PLATFORM_ADMIN");
        assertThat(getGuard).contains("CLINIC_ADMIN").contains("AUDITOR").contains("RECEPTIONIST").contains("PLATFORM_ADMIN");
    }

    @Test
    void mutatingEndpointsAllowClinicAdminReceptionistAndTenantScopedPlatformAdmin() throws Exception {
        Method create = CarePilotLeadController.class.getMethod("create", com.deepthoughtnet.clinic.api.carepilot.dto.LeadDtos.LeadUpsertRequest.class);
        Method update = CarePilotLeadController.class.getMethod("update", java.util.UUID.class, com.deepthoughtnet.clinic.api.carepilot.dto.LeadDtos.LeadUpsertRequest.class);
        Method status = CarePilotLeadController.class.getMethod("updateStatus", java.util.UUID.class, com.deepthoughtnet.clinic.api.carepilot.dto.LeadDtos.LeadStatusUpdateRequest.class);
        Method note = CarePilotLeadController.class.getMethod("addNote", java.util.UUID.class, com.deepthoughtnet.clinic.api.carepilot.dto.LeadDtos.LeadNoteRequest.class);
        Method convert = CarePilotLeadController.class.getMethod("convert", java.util.UUID.class, com.deepthoughtnet.clinic.api.carepilot.dto.LeadDtos.LeadConvertRequest.class);

        for (Method method : new Method[]{create, update, status, note, convert}) {
            String guard = method.getAnnotation(PreAuthorize.class).value();
            assertThat(guard).contains("CLINIC_ADMIN").contains("RECEPTIONIST").contains("PLATFORM_ADMIN");
            assertThat(guard).doesNotContain("AUDITOR");
        }
    }

    @Test
    void activitiesAreReadableByReadRoles() throws Exception {
        Method activities = CarePilotLeadController.class.getMethod("activities", java.util.UUID.class, int.class, int.class);
        String guard = activities.getAnnotation(PreAuthorize.class).value();
        assertThat(guard).contains("CLINIC_ADMIN").contains("AUDITOR").contains("RECEPTIONIST").contains("PLATFORM_ADMIN");
    }
}
