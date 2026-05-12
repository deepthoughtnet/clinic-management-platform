package com.deepthoughtnet.clinic.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.platform.security.Permissions;
import com.deepthoughtnet.clinic.platform.security.RolePermissionMappings;
import com.deepthoughtnet.clinic.platform.security.Roles;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RolePermissionMappingsTest {

    @Test
    void receptionistCanRunDeskButCannotOpenClinicalWorkspace() {
        Set<String> permissions = RolePermissionMappings.permissionsForRole(Roles.RECEPTIONIST);

        assertThat(permissions).contains(
                Permissions.PATIENT_CREATE,
                Permissions.PATIENT_UPDATE,
                Permissions.APPOINTMENT_MANAGE,
                Permissions.QUEUE_READ,
                Permissions.QUEUE_UPDATE
        );
        assertThat(permissions).doesNotContain(
                Permissions.CONSULTATION_CREATE,
                Permissions.CONSULTATION_UPDATE,
                Permissions.CONSULTATION_COMPLETE,
                Permissions.PRESCRIPTION_CREATE,
                Permissions.PRESCRIPTION_FINALIZE
        );
    }

    @Test
    void doctorCanOpenAssignedWorkspaceAndUseAiCopilot() {
        Set<String> permissions = RolePermissionMappings.permissionsForRole(Roles.DOCTOR);

        assertThat(permissions).contains(
                Permissions.CONSULTATION_CREATE,
                Permissions.CONSULTATION_UPDATE,
                Permissions.CONSULTATION_COMPLETE,
                Permissions.PRESCRIPTION_CREATE,
                Permissions.PRESCRIPTION_FINALIZE,
                Permissions.AI_COPILOT_RUN
        );
    }

    @Test
    void billingUserCanCollectPaymentsAndReadReportsWithoutClinicalEditingOrUserManagement() {
        Set<String> permissions = RolePermissionMappings.permissionsForRole(Roles.BILLING_USER);

        assertThat(permissions).contains(
                Permissions.BILLING_READ,
                Permissions.BILLING_CREATE,
                Permissions.BILLING_UPDATE,
                Permissions.BILLING_RECEIPT,
                Permissions.PAYMENT_COLLECT,
                Permissions.REPORT_READ
        );
        assertThat(permissions).doesNotContain(
                Permissions.CONSULTATION_UPDATE,
                Permissions.PRESCRIPTION_FINALIZE,
                Permissions.TENANT_USERS_MANAGE
        );
    }

    @Test
    void refundMutationPermissionsAreLimitedToBillingCapableRoles() {
        Set<String> doctor = RolePermissionMappings.permissionsForRole(Roles.DOCTOR);
        Set<String> auditor = RolePermissionMappings.permissionsForRole(Roles.AUDITOR);
        Set<String> receptionist = RolePermissionMappings.permissionsForRole(Roles.RECEPTIONIST);
        Set<String> billingUser = RolePermissionMappings.permissionsForRole(Roles.BILLING_USER);

        assertThat(doctor).doesNotContain(Permissions.BILLING_CREATE, Permissions.PAYMENT_COLLECT);
        assertThat(auditor).doesNotContain(Permissions.BILLING_CREATE, Permissions.PAYMENT_COLLECT);
        assertThat(receptionist).contains(Permissions.BILLING_CREATE);
        assertThat(billingUser).contains(Permissions.BILLING_CREATE, Permissions.PAYMENT_COLLECT);
    }

    @Test
    void auditorIsReadOnlyForBillingReportsAndClinicalRecords() {
        Set<String> permissions = RolePermissionMappings.permissionsForRole(Roles.AUDITOR);

        assertThat(permissions).contains(
                Permissions.PATIENT_READ,
                Permissions.APPOINTMENT_READ,
                Permissions.BILLING_READ,
                Permissions.BILLING_RECEIPT,
                Permissions.REPORT_READ,
                Permissions.AUDIT_READ
        );
        assertThat(permissions).doesNotContain(
                Permissions.BILLING_CREATE,
                Permissions.BILLING_UPDATE,
                Permissions.PAYMENT_COLLECT,
                Permissions.CONSULTATION_UPDATE,
                Permissions.PRESCRIPTION_CREATE,
                Permissions.PRESCRIPTION_FINALIZE
        );
    }

    @Test
    void pharmaAliasesMapToInventoryDashboardAccessWithoutClinicalEditing() {
        for (String role : Set.of(Roles.PHARMA, Roles.PHARMACY, Roles.PHARMACIST)) {
            Set<String> permissions = RolePermissionMappings.permissionsForRole(role);

            assertThat(permissions).contains(
                    Permissions.DASHBOARD_READ,
                    Permissions.CLINIC_DASHBOARD_READ,
                    Permissions.PRESCRIPTION_READ,
                    Permissions.INVENTORY_READ,
                    Permissions.INVENTORY_MANAGE
            );
            assertThat(permissions).doesNotContain(
                    Permissions.CONSULTATION_CREATE,
                    Permissions.CONSULTATION_UPDATE,
                    Permissions.PRESCRIPTION_FINALIZE
            );
        }
    }
}
