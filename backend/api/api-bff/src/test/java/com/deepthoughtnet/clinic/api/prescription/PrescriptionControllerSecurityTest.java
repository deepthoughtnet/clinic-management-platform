package com.deepthoughtnet.clinic.api.prescription;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class PrescriptionControllerSecurityTest {

    @Test
    void printAndPdfEndpointsRequirePrescriptionPrintPermission() throws Exception {
        Method print = PrescriptionController.class.getMethod("print", java.util.UUID.class);
        Method downloadPdf = PrescriptionController.class.getMethod("downloadPdf", java.util.UUID.class);

        assertThat(print.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('prescription.print')");
        assertThat(downloadPdf.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('prescription.print')");
    }

    @Test
    void clinicalMutationEndpointsRemainRestrictedToPrescriptionCreateOrFinalize() throws Exception {
        Method create = PrescriptionController.class.getMethod(
                "create",
                com.deepthoughtnet.clinic.api.prescription.dto.PrescriptionRequest.class
        );
        Method update = PrescriptionController.class.getMethod(
                "update",
                java.util.UUID.class,
                com.deepthoughtnet.clinic.api.prescription.dto.PrescriptionRequest.class
        );
        Method finalizePrescription = PrescriptionController.class.getMethod("finalizePrescription", java.util.UUID.class);

        assertThat(create.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('prescription.create')");
        assertThat(update.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('prescription.create')");
        assertThat(finalizePrescription.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('prescription.finalize')");
    }
}
