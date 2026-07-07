package com.deepthoughtnet.clinic.api.vaccination;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class VaccineControllerSecurityTest {
    @Test
    void masterEndpointsUseVaccineAccessCheckerGuard() throws Exception {
        Method create = VaccineController.class.getMethod("create", com.deepthoughtnet.clinic.api.vaccination.dto.VaccineRequest.class);
        Method update = VaccineController.class.getMethod("update", java.util.UUID.class, com.deepthoughtnet.clinic.api.vaccination.dto.VaccineRequest.class);
        Method deactivate = VaccineController.class.getMethod("deactivate", java.util.UUID.class);
        Method importTemplate = VaccineController.class.getMethod("importTemplate");
        Method export = VaccineController.class.getMethod("export");
        Method importCsv = VaccineController.class.getMethod("importCsv", org.springframework.web.multipart.MultipartFile.class);

        assertThat(create.getAnnotation(PreAuthorize.class).value()).isEqualTo("@vaccineAccessChecker.canManageVaccineMaster()");
        assertThat(update.getAnnotation(PreAuthorize.class).value()).isEqualTo("@vaccineAccessChecker.canManageVaccineMaster()");
        assertThat(deactivate.getAnnotation(PreAuthorize.class).value()).isEqualTo("@vaccineAccessChecker.canManageVaccineMaster()");
        assertThat(importTemplate.getAnnotation(PreAuthorize.class).value()).isEqualTo("@vaccineAccessChecker.canManageVaccineMaster()");
        assertThat(export.getAnnotation(PreAuthorize.class).value()).isEqualTo("@vaccineAccessChecker.canManageVaccineMaster()");
        assertThat(importCsv.getAnnotation(PreAuthorize.class).value()).isEqualTo("@vaccineAccessChecker.canManageVaccineMaster()");
    }
}
