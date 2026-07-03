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
                Permissions.APPOINTMENT_CHECKIN_PAYMENT_BYPASS,
                Permissions.QUEUE_READ,
                Permissions.QUEUE_UPDATE,
                Permissions.NOTIFICATION_READ,
                Permissions.PAYMENT_COLLECT
        );
        assertThat(permissions).doesNotContain(
                Permissions.BILLING_UPDATE,
                Permissions.CONSULTATION_CREATE,
                Permissions.CONSULTATION_UPDATE,
                Permissions.CONSULTATION_COMPLETE,
                Permissions.PRESCRIPTION_CREATE,
                Permissions.PRESCRIPTION_FINALIZE,
                Permissions.BILLING_UPDATE
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
        assertThat(permissions).doesNotContain(
                Permissions.LAB_ORDER_COLLECT_SAMPLE,
                Permissions.LAB_ORDER_RESULT_ENTRY,
                Permissions.LAB_ORDER_GENERATE_REPORT,
                Permissions.LAB_ORDER_REVIEW
        );
    }

    @Test
    void clinicAdminKeepsClinicalVisibilityWhileGettingFullLabAccess() {
        Set<String> permissions = RolePermissionMappings.permissionsForRole(Roles.CLINIC_ADMIN);

        assertThat(permissions).contains(
                Permissions.CONSULTATION_READ,
                Permissions.PRESCRIPTION_READ,
                Permissions.LAB_TEST_READ,
                Permissions.LAB_TEST_MANAGE,
                Permissions.LAB_ORDER_CREATE,
                Permissions.LAB_ORDER_READ,
                Permissions.LAB_ORDER_COLLECT_PAYMENT,
                Permissions.LAB_ORDER_COLLECT_SAMPLE,
                Permissions.LAB_ORDER_RESULT_ENTRY,
                Permissions.LAB_ORDER_GENERATE_REPORT,
                Permissions.LAB_ORDER_REVIEW
        );
        assertThat(permissions).doesNotContain(
                Permissions.CONSULTATION_CREATE,
                Permissions.CONSULTATION_UPDATE,
                Permissions.CONSULTATION_COMPLETE,
                Permissions.PRESCRIPTION_CREATE,
                Permissions.PRESCRIPTION_FINALIZE,
                Permissions.PRESCRIPTION_PRINT,
                Permissions.PRESCRIPTION_SEND
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
                Permissions.TENANT_USERS_MANAGE,
                Permissions.INVENTORY_MANAGE
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
        assertThat(receptionist).contains(Permissions.BILLING_CREATE, Permissions.PAYMENT_COLLECT);
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
                Permissions.PRESCRIPTION_FINALIZE,
                Permissions.INVENTORY_MANAGE
        );
    }

    @Test
    void labTechnicianCanCollectSampleAndEnterResultsButCannotVerify() {
        Set<String> permissions = RolePermissionMappings.permissionsForRole(Roles.LAB_TECHNICIAN);

        assertThat(permissions).contains(
                Permissions.LAB_TEST_READ,
                Permissions.LAB_ORDER_READ,
                Permissions.LAB_ORDER_COLLECT_SAMPLE,
                Permissions.LAB_ORDER_RESULT_ENTRY
        );
        assertThat(permissions).doesNotContain(
                Permissions.LAB_ORDER_REVIEW,
                Permissions.LAB_ORDER_GENERATE_REPORT
        );
    }

    @Test
    void labAssistantCanCollectSampleOnly() {
        Set<String> permissions = RolePermissionMappings.permissionsForRole(Roles.LAB_ASSISTANT);

        assertThat(permissions).contains(
                Permissions.LAB_TEST_READ,
                Permissions.LAB_ORDER_READ,
                Permissions.LAB_ORDER_COLLECT_SAMPLE
        );
        assertThat(permissions).doesNotContain(
                Permissions.LAB_ORDER_RESULT_ENTRY,
                Permissions.LAB_ORDER_REVIEW,
                Permissions.LAB_ORDER_GENERATE_REPORT
        );
    }

    @Test
    void labApproverCanVerifyAndGenerateReports() {
        Set<String> permissions = RolePermissionMappings.permissionsForRole(Roles.LAB_APPROVER);

        assertThat(permissions).contains(
                Permissions.LAB_TEST_READ,
                Permissions.LAB_ORDER_READ,
                Permissions.LAB_ORDER_REVIEW,
                Permissions.LAB_ORDER_GENERATE_REPORT,
                Permissions.REPORT_READ,
                Permissions.AUDIT_READ
        );
        assertThat(permissions).doesNotContain(
                Permissions.LAB_ORDER_COLLECT_SAMPLE,
                Permissions.LAB_ORDER_RESULT_ENTRY
        );
    }

    @Test
    void labFrontDeskCanCreateOrdersAndCollectPaymentsOnly() {
        Set<String> permissions = RolePermissionMappings.permissionsForRole(Roles.LAB_FRONT_DESK);

        assertThat(permissions).contains(
                Permissions.LAB_TEST_READ,
                Permissions.LAB_ORDER_READ,
                Permissions.LAB_ORDER_CREATE,
                Permissions.LAB_ORDER_COLLECT_PAYMENT,
                Permissions.PATIENT_CREATE,
                Permissions.PATIENT_READ
        );
        assertThat(permissions).doesNotContain(
                Permissions.LAB_TEST_MANAGE,
                Permissions.LAB_ORDER_COLLECT_SAMPLE,
                Permissions.LAB_ORDER_RESULT_ENTRY,
                Permissions.LAB_ORDER_REVIEW,
                Permissions.LAB_ORDER_GENERATE_REPORT
        );
    }

    @Test
    void clinicAdminHasFullLabAccess() {
        Set<String> permissions = RolePermissionMappings.permissionsForRole(Roles.CLINIC_ADMIN);

        assertThat(permissions).contains(
                Permissions.LAB_TEST_READ,
                Permissions.LAB_TEST_MANAGE,
                Permissions.LAB_ORDER_CREATE,
                Permissions.LAB_ORDER_READ,
                Permissions.LAB_ORDER_COLLECT_PAYMENT,
                Permissions.LAB_ORDER_COLLECT_SAMPLE,
                Permissions.LAB_ORDER_RESULT_ENTRY,
                Permissions.LAB_ORDER_GENERATE_REPORT,
                Permissions.LAB_ORDER_REVIEW
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
                    Permissions.PRESCRIPTION_PRINT,
                    Permissions.MEDICINE_READ,
                    Permissions.BILLING_CREATE,
                    Permissions.BILLING_READ,
                    Permissions.BILLING_RECEIPT,
                    Permissions.PAYMENT_COLLECT,
                    Permissions.INVENTORY_READ,
                    Permissions.INVENTORY_MANAGE,
                    Permissions.REPORT_READ
            );
            assertThat(permissions).doesNotContain(
                    Permissions.USER_READ,
                    Permissions.CONSULTATION_CREATE,
                    Permissions.CONSULTATION_UPDATE,
                    Permissions.CONSULTATION_COMPLETE,
                    Permissions.PRESCRIPTION_CREATE,
                    Permissions.PRESCRIPTION_FINALIZE,
                    Permissions.PRESCRIPTION_SEND,
                    Permissions.NOTIFICATION_SEND,
                    Permissions.NOTIFICATION_MANAGE
            );
        }
    }

    @Test
    void inventoryManagerCanManageStockButCannotRunPosPayments() {
        Set<String> permissions = RolePermissionMappings.permissionsForRole(Roles.PHARMACY_INVENTORY_MANAGER);

        assertThat(permissions).contains(
                Permissions.DASHBOARD_READ,
                Permissions.CLINIC_DASHBOARD_READ,
                Permissions.PATIENT_READ,
                Permissions.PRESCRIPTION_READ,
                Permissions.PRESCRIPTION_PRINT,
                Permissions.MEDICINE_READ,
                Permissions.INVENTORY_READ,
                Permissions.INVENTORY_CREATE,
                Permissions.INVENTORY_UPDATE,
                Permissions.INVENTORY_MANAGE,
                Permissions.REPORT_READ
        );
        assertThat(permissions).doesNotContain(
                Permissions.BILLING_CREATE,
                Permissions.BILLING_READ,
                Permissions.BILLING_RECEIPT,
                Permissions.PAYMENT_COLLECT
        );
    }

    @Test
    void posUserCanSellMedicineWithoutStockMasterEditing() {
        Set<String> permissions = RolePermissionMappings.permissionsForRole(Roles.PHARMACY_POS_USER);

        assertThat(permissions).contains(
                Permissions.DASHBOARD_READ,
                Permissions.CLINIC_DASHBOARD_READ,
                Permissions.PATIENT_READ,
                Permissions.PRESCRIPTION_READ,
                Permissions.PRESCRIPTION_PRINT,
                Permissions.MEDICINE_READ,
                Permissions.BILLING_CREATE,
                Permissions.BILLING_READ,
                Permissions.BILLING_RECEIPT,
                Permissions.PAYMENT_COLLECT,
                Permissions.INVENTORY_READ,
                Permissions.REPORT_READ
        );
        assertThat(permissions).doesNotContain(
                Permissions.INVENTORY_CREATE,
                Permissions.INVENTORY_UPDATE,
                Permissions.INVENTORY_MANAGE
        );
    }

    @Test
    void carePilotLeadPermissionsFollowOperationalRoleModel() {
        Set<String> clinicAdmin = RolePermissionMappings.permissionsForRole(Roles.CLINIC_ADMIN);
        Set<String> receptionist = RolePermissionMappings.permissionsForRole(Roles.RECEPTIONIST);
        Set<String> auditor = RolePermissionMappings.permissionsForRole(Roles.AUDITOR);
        Set<String> doctor = RolePermissionMappings.permissionsForRole(Roles.DOCTOR);
        Set<String> billing = RolePermissionMappings.permissionsForRole(Roles.BILLING_USER);

        assertThat(clinicAdmin).contains(Permissions.CAREPILOT_LEAD_READ, Permissions.CAREPILOT_LEAD_CREATE, Permissions.CAREPILOT_LEAD_UPDATE, Permissions.CAREPILOT_LEAD_CONVERT);
        assertThat(receptionist).contains(Permissions.CAREPILOT_LEAD_READ, Permissions.CAREPILOT_LEAD_CREATE, Permissions.CAREPILOT_LEAD_UPDATE, Permissions.CAREPILOT_LEAD_CONVERT);
        assertThat(auditor).contains(Permissions.CAREPILOT_LEAD_READ);
        assertThat(doctor).doesNotContain(Permissions.CAREPILOT_LEAD_READ, Permissions.CAREPILOT_LEAD_CREATE, Permissions.CAREPILOT_LEAD_UPDATE, Permissions.CAREPILOT_LEAD_CONVERT);
        assertThat(billing).doesNotContain(Permissions.CAREPILOT_LEAD_READ, Permissions.CAREPILOT_LEAD_CREATE, Permissions.CAREPILOT_LEAD_UPDATE, Permissions.CAREPILOT_LEAD_CONVERT);
    }

    @Test
    void checkInPaymentBypassIsLimitedToDeskLeadershipRoles() {
        Set<String> clinicAdmin = RolePermissionMappings.permissionsForRole(Roles.CLINIC_ADMIN);
        Set<String> receptionist = RolePermissionMappings.permissionsForRole(Roles.RECEPTIONIST);
        Set<String> auditor = RolePermissionMappings.permissionsForRole(Roles.AUDITOR);
        Set<String> doctor = RolePermissionMappings.permissionsForRole(Roles.DOCTOR);
        Set<String> billing = RolePermissionMappings.permissionsForRole(Roles.BILLING_USER);

        assertThat(clinicAdmin).contains(Permissions.APPOINTMENT_CHECKIN_PAYMENT_BYPASS);
        assertThat(receptionist).contains(Permissions.APPOINTMENT_CHECKIN_PAYMENT_BYPASS);
        assertThat(auditor).doesNotContain(Permissions.APPOINTMENT_CHECKIN_PAYMENT_BYPASS);
        assertThat(doctor).doesNotContain(Permissions.APPOINTMENT_CHECKIN_PAYMENT_BYPASS);
        assertThat(billing).doesNotContain(Permissions.APPOINTMENT_CHECKIN_PAYMENT_BYPASS);
    }

    @Test
    void clinicAdminCanReadOperationalNotificationsWhileDoctorCannot() {
        Set<String> clinicAdmin = RolePermissionMappings.permissionsForRole(Roles.CLINIC_ADMIN);
        Set<String> tenantAdmin = RolePermissionMappings.permissionsForRole(Roles.TENANT_ADMIN);
        Set<String> doctor = RolePermissionMappings.permissionsForRole(Roles.DOCTOR);

        assertThat(clinicAdmin).contains(Permissions.NOTIFICATION_READ);
        assertThat(tenantAdmin).contains(Permissions.NOTIFICATION_READ);
        assertThat(doctor).doesNotContain(Permissions.NOTIFICATION_READ);
    }

    @Test
    void voiceTestPermissionIsLimitedToOperationalAiRoles() {
        Set<String> clinicAdmin = RolePermissionMappings.permissionsForRole(Roles.CLINIC_ADMIN);
        Set<String> tenantAdmin = RolePermissionMappings.permissionsForRole(Roles.TENANT_ADMIN);
        Set<String> receptionist = RolePermissionMappings.permissionsForRole(Roles.RECEPTIONIST);
        Set<String> doctor = RolePermissionMappings.permissionsForRole(Roles.DOCTOR);

        assertThat(clinicAdmin).contains(Permissions.AI_VOICE_TEST);
        assertThat(tenantAdmin).contains(Permissions.AI_VOICE_TEST);
        assertThat(receptionist).contains(Permissions.AI_VOICE_TEST);
        assertThat(doctor).doesNotContain(Permissions.AI_VOICE_TEST);
    }

    @Test
    void carePilotWebinarPermissionsFollowOperationalRoleModel() {
        Set<String> clinicAdmin = RolePermissionMappings.permissionsForRole(Roles.CLINIC_ADMIN);
        Set<String> receptionist = RolePermissionMappings.permissionsForRole(Roles.RECEPTIONIST);
        Set<String> auditor = RolePermissionMappings.permissionsForRole(Roles.AUDITOR);
        Set<String> doctor = RolePermissionMappings.permissionsForRole(Roles.DOCTOR);
        Set<String> billing = RolePermissionMappings.permissionsForRole(Roles.BILLING_USER);

        assertThat(clinicAdmin).contains(Permissions.CAREPILOT_WEBINAR_READ, Permissions.CAREPILOT_WEBINAR_MANAGE);
        assertThat(receptionist).contains(Permissions.CAREPILOT_WEBINAR_READ, Permissions.CAREPILOT_WEBINAR_MANAGE);
        assertThat(auditor).contains(Permissions.CAREPILOT_WEBINAR_READ);
        assertThat(doctor).doesNotContain(Permissions.CAREPILOT_WEBINAR_READ, Permissions.CAREPILOT_WEBINAR_MANAGE);
        assertThat(billing).doesNotContain(Permissions.CAREPILOT_WEBINAR_READ, Permissions.CAREPILOT_WEBINAR_MANAGE);
    }
}
