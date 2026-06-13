package com.deepthoughtnet.clinic.api.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.inventory.dto.DispenseRequest;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class DispensingControllerSecurityTest {

    @Test
    void queueAndViewRemainReadableForPrescriptionAndPharmacyRoles() throws Exception {
        Method queue = DispensingController.class.getMethod("queue");
        Method view = DispensingController.class.getMethod("view", UUID.class);

        assertThat(queue.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('prescription.read')");
        assertThat(view.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@permissionChecker.hasPermission('inventory.manage') or @permissionChecker.hasPermission('prescription.read')");
    }

    @Test
    void dispenseAndBillMutationsStayBoundToPharmacyBillingPermissions() throws Exception {
        Method dispense = DispensingController.class.getMethod("dispense", UUID.class, DispenseRequest.class);
        Method generateBill = DispensingController.class.getMethod("generateBill", UUID.class);

        assertThat(dispense.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@permissionChecker.hasPermission('inventory.manage')");
        assertThat(generateBill.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@permissionChecker.hasPermission('inventory.manage')");
    }
}
