package com.deepthoughtnet.clinic.platform.security;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class RolePermissionMappings {
    private static final Set<String> CLINIC_GENERATION_MANAGER_PERMISSIONS = Set.of(
            Permissions.CLINIC_GENERATION_CUSTOMER_READ,
            Permissions.CLINIC_GENERATION_CUSTOMER_MANAGE,
            Permissions.CLINIC_GENERATION_ITEM_READ,
            Permissions.CLINIC_GENERATION_ITEM_MANAGE,
            Permissions.CLINIC_GENERATION_CLINIC_READ,
            Permissions.CLINIC_GENERATION_CLINIC_CREATE,
            Permissions.CLINIC_GENERATION_CLINIC_UPDATE,
            Permissions.CLINIC_GENERATION_CLINIC_SUBMIT,
            Permissions.CLINIC_GENERATION_CLINIC_APPROVE,
            Permissions.CLINIC_GENERATION_CLINIC_REJECT,
            Permissions.CLINIC_GENERATION_CLINIC_ISSUE,
            Permissions.CLINIC_GENERATION_CLINIC_CANCEL,
            Permissions.CLINIC_GENERATION_CLINIC_PDF,
            Permissions.CLINIC_GENERATION_CLINIC_SEND,
            Permissions.CLINIC_GENERATION_NUMBERING_READ,
            Permissions.CLINIC_GENERATION_NUMBERING_MANAGE
    );

    private static final Set<String> CLINIC_GENERATION_CREATOR_PERMISSIONS = Set.of(
            Permissions.CLINIC_GENERATION_CUSTOMER_READ,
            Permissions.CLINIC_GENERATION_ITEM_READ,
            Permissions.CLINIC_GENERATION_CLINIC_READ,
            Permissions.CLINIC_GENERATION_CLINIC_CREATE,
            Permissions.CLINIC_GENERATION_CLINIC_UPDATE,
            Permissions.CLINIC_GENERATION_CLINIC_SUBMIT,
            Permissions.CLINIC_GENERATION_CLINIC_PDF
    );

    private static final Set<String> CLINIC_GENERATION_APPROVER_PERMISSIONS = Set.of(
            Permissions.CLINIC_GENERATION_CUSTOMER_READ,
            Permissions.CLINIC_GENERATION_ITEM_READ,
            Permissions.CLINIC_GENERATION_CLINIC_READ,
            Permissions.CLINIC_GENERATION_CLINIC_APPROVE,
            Permissions.CLINIC_GENERATION_CLINIC_REJECT,
            Permissions.CLINIC_GENERATION_CLINIC_ISSUE,
            Permissions.CLINIC_GENERATION_CLINIC_PDF,
            Permissions.CLINIC_GENERATION_CLINIC_SEND
    );

    private static final Set<String> CLINIC_GENERATION_VIEWER_PERMISSIONS = Set.of(
            Permissions.CLINIC_GENERATION_CUSTOMER_READ,
            Permissions.CLINIC_GENERATION_ITEM_READ,
            Permissions.CLINIC_GENERATION_CLINIC_READ,
            Permissions.CLINIC_GENERATION_CLINIC_PDF
    );

    private static final Set<String> RECONCILIATION_MANAGER_PERMISSIONS = Set.of(
            Permissions.RECONCILIATION_BATCH_READ,
            Permissions.RECONCILIATION_BATCH_MANAGE,
            Permissions.RECONCILIATION_STATEMENT_UPLOAD,
            Permissions.RECONCILIATION_RUN,
            Permissions.RECONCILIATION_MATCH_READ,
            Permissions.RECONCILIATION_MATCH_MANAGE,
            Permissions.RECONCILIATION_EXCEPTION_READ,
            Permissions.RECONCILIATION_EXCEPTION_MANAGE,
            Permissions.DECISIONING_EXECUTION_RUN,
            Permissions.AUDIT_READ,
            Permissions.AI_COPILOT_READ,
            Permissions.AI_COPILOT_RUN,
            Permissions.AI_COPILOT_RECONCILIATION_READ,
            Permissions.AI_COPILOT_RECONCILIATION_RUN
    );

    private static final Set<String> RECONCILIATION_OPERATOR_PERMISSIONS = Set.of(
            Permissions.RECONCILIATION_BATCH_READ,
            Permissions.RECONCILIATION_BATCH_MANAGE,
            Permissions.RECONCILIATION_STATEMENT_UPLOAD,
            Permissions.RECONCILIATION_RUN,
            Permissions.RECONCILIATION_MATCH_READ,
            Permissions.RECONCILIATION_EXCEPTION_READ,
            Permissions.AI_COPILOT_READ,
            Permissions.AI_COPILOT_RUN,
            Permissions.AI_COPILOT_RECONCILIATION_READ,
            Permissions.AI_COPILOT_RECONCILIATION_RUN
    );

    private static final Set<String> RECONCILIATION_REVIEWER_PERMISSIONS = Set.of(
            Permissions.RECONCILIATION_BATCH_READ,
            Permissions.RECONCILIATION_MATCH_READ,
            Permissions.RECONCILIATION_MATCH_MANAGE,
            Permissions.RECONCILIATION_EXCEPTION_READ,
            Permissions.RECONCILIATION_EXCEPTION_MANAGE,
            Permissions.AI_COPILOT_READ,
            Permissions.AI_COPILOT_RUN,
            Permissions.AI_COPILOT_RECONCILIATION_READ,
            Permissions.AI_COPILOT_RECONCILIATION_RUN
    );

    private static final Set<String> RECONCILIATION_VIEWER_PERMISSIONS = Set.of(
            Permissions.RECONCILIATION_BATCH_READ,
            Permissions.RECONCILIATION_MATCH_READ,
            Permissions.RECONCILIATION_EXCEPTION_READ
    );

    private static final Set<String> DECISIONING_MANAGER_PERMISSIONS = Set.of(
            Permissions.DECISIONING_POLICY_READ,
            Permissions.DECISIONING_POLICY_MANAGE,
            Permissions.DECISIONING_EXECUTION_READ,
            Permissions.DECISIONING_EXECUTION_RUN,
            Permissions.DECISIONING_EXECUTION_OVERRIDE,
            Permissions.AUDIT_READ,
            Permissions.AI_COPILOT_READ,
            Permissions.AI_COPILOT_RUN
    );

    private static final Set<String> DECISIONING_VIEWER_PERMISSIONS = Set.of(
            Permissions.DECISIONING_POLICY_READ,
            Permissions.DECISIONING_EXECUTION_READ,
            Permissions.AI_COPILOT_READ
    );

    private static final Set<String> TENANT_ADMIN_PERMISSIONS = Set.of(
            Permissions.DASHBOARD_READ,
            Permissions.CLINIC_READ,
            Permissions.CLINIC_DOCUMENT_UPLOAD,
            Permissions.CLINIC_DOCUMENT_AGENT_UPLOAD,
            Permissions.CLINIC_DOCUMENT_READ,
            Permissions.AGENT_INTAKE_CREATE,
            Permissions.AGENT_INTAKE_READ,
            Permissions.AGENT_INTAKE_SOURCE_READ,
            Permissions.AGENT_INTAKE_SOURCE_MANAGE,
            Permissions.AGENT_INTAKE_RUN,
            Permissions.AGENT_INTAKE_RUN_READ,
            Permissions.AGENT_INTAKE_RETRY,
            Permissions.AGENT_INTAKE_ITEM_IGNORE,
            Permissions.CLINIC_EXTRACTION_RUN,
            Permissions.CLINIC_EXTRACTION_JOBS_READ,
            Permissions.CLINIC_EXTRACTION_JOBS_MANAGE,
            Permissions.CLINIC_REVIEW,
            Permissions.CLINIC_SUBMIT_FOR_APPROVAL,
            Permissions.CLINIC_APPROVE,
            Permissions.CLINIC_REJECT,
            Permissions.CLINIC_AUDIT_READ,
            Permissions.CLINIC_DOCTOR_READ,
            Permissions.CLINIC_DOCTOR_MANAGE,
            Permissions.CLINIC_DOCTOR_RESUBMISSION,
            Permissions.CLINIC_ARCHIVE,
            Permissions.CLINIC_DASHBOARD_READ,
            Permissions.TENANT_USERS_READ,
            Permissions.TENANT_USERS_MANAGE,
            Permissions.TENANT_USERS_ROLE_ASSIGN,
            Permissions.TENANT_USERS_RESET_PASSWORD,
            Permissions.NOTIFICATION_READ,
            Permissions.NOTIFICATION_MANAGE,
            Permissions.NOTIFICATION_SEND,
            Permissions.NOTIFICATION_RETRY,
            Permissions.AUDIT_READ,
            Permissions.AUDIT_EXPORT,
            Permissions.DECISIONING_POLICY_READ,
            Permissions.DECISIONING_POLICY_MANAGE,
            Permissions.DECISIONING_EXECUTION_READ,
            Permissions.DECISIONING_EXECUTION_RUN,
            Permissions.DECISIONING_EXECUTION_OVERRIDE,
            Permissions.AI_COPILOT_CLINIC_READ,
            Permissions.AI_COPILOT_CLINIC_RUN,
            Permissions.AI_COPILOT_RECONCILIATION_READ,
            Permissions.AI_COPILOT_RECONCILIATION_RUN,
            Permissions.AI_COPILOT_READ,
            Permissions.AI_COPILOT_RUN,
            Permissions.CLINIC_GENERATION_CUSTOMER_READ,
            Permissions.CLINIC_GENERATION_CUSTOMER_MANAGE,
            Permissions.CLINIC_GENERATION_ITEM_READ,
            Permissions.CLINIC_GENERATION_ITEM_MANAGE,
            Permissions.CLINIC_GENERATION_CLINIC_READ,
            Permissions.CLINIC_GENERATION_CLINIC_CREATE,
            Permissions.CLINIC_GENERATION_CLINIC_UPDATE,
            Permissions.CLINIC_GENERATION_CLINIC_SUBMIT,
            Permissions.CLINIC_GENERATION_CLINIC_APPROVE,
            Permissions.CLINIC_GENERATION_CLINIC_REJECT,
            Permissions.CLINIC_GENERATION_CLINIC_ISSUE,
            Permissions.CLINIC_GENERATION_CLINIC_CANCEL,
            Permissions.CLINIC_GENERATION_CLINIC_PDF,
            Permissions.CLINIC_GENERATION_CLINIC_SEND,
            Permissions.CLINIC_GENERATION_NUMBERING_READ,
            Permissions.CLINIC_GENERATION_NUMBERING_MANAGE,
            Permissions.RECONCILIATION_BATCH_READ,
            Permissions.RECONCILIATION_BATCH_MANAGE,
            Permissions.RECONCILIATION_STATEMENT_UPLOAD,
            Permissions.RECONCILIATION_RUN,
            Permissions.RECONCILIATION_MATCH_READ,
            Permissions.RECONCILIATION_MATCH_MANAGE,
            Permissions.RECONCILIATION_EXCEPTION_READ,
            Permissions.RECONCILIATION_EXCEPTION_MANAGE
    );

    private static final Set<String> CLINIC_READ_PERMISSIONS = Set.of(
            Permissions.DASHBOARD_READ,
            Permissions.CLINIC_READ,
            Permissions.CLINIC_DOCUMENT_READ,
            Permissions.AGENT_INTAKE_READ,
            Permissions.AGENT_INTAKE_SOURCE_READ,
            Permissions.AGENT_INTAKE_RUN_READ,
            Permissions.CLINIC_AUDIT_READ,
            Permissions.CLINIC_EXTRACTION_JOBS_READ,
            Permissions.CLINIC_DOCTOR_READ,
            Permissions.CLINIC_DASHBOARD_READ,
            Permissions.CLINIC_GENERATION_CUSTOMER_READ,
            Permissions.CLINIC_GENERATION_ITEM_READ,
            Permissions.CLINIC_GENERATION_CLINIC_READ,
            Permissions.CLINIC_GENERATION_CLINIC_PDF,
            Permissions.RECONCILIATION_BATCH_READ,
            Permissions.RECONCILIATION_MATCH_READ,
            Permissions.RECONCILIATION_EXCEPTION_READ,
            Permissions.DECISIONING_POLICY_READ,
            Permissions.DECISIONING_EXECUTION_READ,
            Permissions.AI_COPILOT_READ
    );

    private static final Set<String> AUDITOR_PERMISSIONS = Set.of(
            Permissions.DASHBOARD_READ,
            Permissions.CLINIC_READ,
            Permissions.CLINIC_DOCUMENT_READ,
            Permissions.AGENT_INTAKE_READ,
            Permissions.AGENT_INTAKE_SOURCE_READ,
            Permissions.AGENT_INTAKE_RUN_READ,
            Permissions.CLINIC_AUDIT_READ,
            Permissions.CLINIC_EXTRACTION_JOBS_READ,
            Permissions.CLINIC_DOCTOR_READ,
            Permissions.CLINIC_DASHBOARD_READ,
            Permissions.NOTIFICATION_READ,
            Permissions.AUDIT_READ,
            Permissions.AI_COPILOT_READ,
            Permissions.AI_COPILOT_CLINIC_READ,
            Permissions.AI_COPILOT_RECONCILIATION_READ,
            Permissions.CLINIC_GENERATION_CUSTOMER_READ,
            Permissions.CLINIC_GENERATION_ITEM_READ,
            Permissions.CLINIC_GENERATION_CLINIC_READ,
            Permissions.CLINIC_GENERATION_CLINIC_PDF,
            Permissions.RECONCILIATION_BATCH_READ,
            Permissions.RECONCILIATION_MATCH_READ,
            Permissions.RECONCILIATION_EXCEPTION_READ,
            Permissions.DECISIONING_POLICY_READ,
            Permissions.DECISIONING_EXECUTION_READ
    );

    private static final Set<String> PLATFORM_TENANT_SUPPORT_PERMISSIONS = Set.of(
            Permissions.DASHBOARD_READ,
            Permissions.CLINIC_READ,
            Permissions.CLINIC_DOCUMENT_READ,
            Permissions.CLINIC_EXTRACTION_JOBS_READ,
            Permissions.CLINIC_AUDIT_READ,
            Permissions.CLINIC_DOCTOR_READ,
            Permissions.CLINIC_DASHBOARD_READ,
            Permissions.AGENT_INTAKE_READ,
            Permissions.AGENT_INTAKE_SOURCE_READ,
            Permissions.AGENT_INTAKE_RUN_READ,
            Permissions.CLINIC_GENERATION_CUSTOMER_READ,
            Permissions.CLINIC_GENERATION_ITEM_READ,
            Permissions.CLINIC_GENERATION_CLINIC_READ,
            Permissions.CLINIC_GENERATION_CLINIC_PDF,
            Permissions.RECONCILIATION_BATCH_READ,
            Permissions.RECONCILIATION_MATCH_READ,
            Permissions.RECONCILIATION_EXCEPTION_READ,
            Permissions.DECISIONING_POLICY_READ,
            Permissions.DECISIONING_EXECUTION_READ,
            Permissions.AI_COPILOT_READ,
            Permissions.AI_COPILOT_CLINIC_READ,
            Permissions.AI_COPILOT_RECONCILIATION_READ
    );

    private static final Map<String, Set<String>> ROLE_PERMISSIONS = Map.ofEntries(
            Map.entry(Roles.ADMIN, TENANT_ADMIN_PERMISSIONS),
            Map.entry(Roles.TENANT_ADMIN, TENANT_ADMIN_PERMISSIONS),
            Map.entry(Roles.CLINIC_ADMIN, TENANT_ADMIN_PERMISSIONS),
            Map.entry(Roles.CLINIC_REVIEWER, Set.of(
                    Permissions.DASHBOARD_READ,
                    Permissions.CLINIC_READ,
                    Permissions.CLINIC_DOCUMENT_UPLOAD,
                    Permissions.CLINIC_DOCUMENT_READ,
                    Permissions.AGENT_INTAKE_SOURCE_READ,
                    Permissions.AGENT_INTAKE_RUN,
                    Permissions.AGENT_INTAKE_RUN_READ,
                    Permissions.CLINIC_EXTRACTION_RUN,
                    Permissions.CLINIC_EXTRACTION_JOBS_READ,
                    Permissions.CLINIC_EXTRACTION_JOBS_MANAGE,
                    Permissions.CLINIC_REVIEW,
                    Permissions.CLINIC_SUBMIT_FOR_APPROVAL,
                    Permissions.CLINIC_AUDIT_READ,
                    Permissions.CLINIC_DOCTOR_READ,
                    Permissions.CLINIC_DOCTOR_RESUBMISSION,
                    Permissions.CLINIC_DASHBOARD_READ,
                    Permissions.CLINIC_GENERATION_CUSTOMER_READ,
                    Permissions.CLINIC_GENERATION_ITEM_READ,
                    Permissions.CLINIC_GENERATION_CLINIC_READ,
                    Permissions.CLINIC_GENERATION_CLINIC_CREATE,
                    Permissions.CLINIC_GENERATION_CLINIC_UPDATE,
                    Permissions.CLINIC_GENERATION_CLINIC_SUBMIT,
                    Permissions.CLINIC_GENERATION_CLINIC_PDF,
                    Permissions.AI_COPILOT_CLINIC_READ,
                    Permissions.AI_COPILOT_CLINIC_RUN,
                    Permissions.AI_COPILOT_READ,
                    Permissions.AI_COPILOT_RUN
            )),
            Map.entry(Roles.CLINIC_APPROVER, Set.of(
                    Permissions.DASHBOARD_READ,
                    Permissions.CLINIC_READ,
                    Permissions.CLINIC_DOCUMENT_READ,
                    Permissions.CLINIC_APPROVE,
                    Permissions.CLINIC_REJECT,
                    Permissions.CLINIC_AUDIT_READ,
                    Permissions.CLINIC_DOCTOR_READ,
                    Permissions.CLINIC_DASHBOARD_READ,
                    Permissions.CLINIC_GENERATION_CUSTOMER_READ,
                    Permissions.CLINIC_GENERATION_ITEM_READ,
                    Permissions.CLINIC_GENERATION_CLINIC_READ,
                    Permissions.CLINIC_GENERATION_CLINIC_APPROVE,
                    Permissions.CLINIC_GENERATION_CLINIC_REJECT,
                    Permissions.CLINIC_GENERATION_CLINIC_ISSUE,
                    Permissions.CLINIC_GENERATION_CLINIC_PDF,
                    Permissions.CLINIC_GENERATION_CLINIC_SEND,
                    Permissions.AI_COPILOT_CLINIC_READ,
                    Permissions.AI_COPILOT_CLINIC_RUN,
                    Permissions.AI_COPILOT_READ,
                    Permissions.AI_COPILOT_RUN
            )),
            Map.entry(Roles.CLINIC_GENERATION_CREATOR, CLINIC_GENERATION_CREATOR_PERMISSIONS),
            Map.entry(Roles.CLINIC_GENERATION_APPROVER, CLINIC_GENERATION_APPROVER_PERMISSIONS),
            Map.entry(Roles.CLINIC_GENERATION_MANAGER, CLINIC_GENERATION_MANAGER_PERMISSIONS),
            Map.entry(Roles.CLINIC_GENERATION_VIEWER, CLINIC_GENERATION_VIEWER_PERMISSIONS),
            Map.entry(Roles.RECONCILIATION_OPERATOR, RECONCILIATION_OPERATOR_PERMISSIONS),
            Map.entry(Roles.RECONCILIATION_REVIEWER, RECONCILIATION_REVIEWER_PERMISSIONS),
            Map.entry(Roles.RECONCILIATION_MANAGER, RECONCILIATION_MANAGER_PERMISSIONS),
            Map.entry(Roles.RECONCILIATION_VIEWER, RECONCILIATION_VIEWER_PERMISSIONS),
            Map.entry(Roles.DECISIONING_MANAGER, DECISIONING_MANAGER_PERMISSIONS),
            Map.entry(Roles.DECISIONING_VIEWER, DECISIONING_VIEWER_PERMISSIONS),
            Map.entry(Roles.AUDITOR, AUDITOR_PERMISSIONS),
            Map.entry(Roles.CLINIC_AUDITOR, AUDITOR_PERMISSIONS),
            Map.entry(Roles.VIEWER, CLINIC_READ_PERMISSIONS),
            Map.entry(Roles.CLINIC_VIEWER, CLINIC_READ_PERMISSIONS),
            Map.entry(Roles.AGENT_OPERATOR, Set.of(
                    Permissions.AGENT_INTAKE_SOURCE_READ,
                    Permissions.AGENT_INTAKE_RUN,
                    Permissions.AGENT_INTAKE_RUN_READ,
                    Permissions.AGENT_INTAKE_RETRY,
                    Permissions.AGENT_INTAKE_ITEM_IGNORE
            )),
            Map.entry(Roles.PLATFORM_ADMIN, Set.of(
                    Permissions.PLATFORM_TENANTS_READ,
                    Permissions.PLATFORM_TENANTS_MANAGE,
                    Permissions.NOTIFICATION_READ,
                    Permissions.NOTIFICATION_MANAGE
            )),
            Map.entry(Roles.PLATFORM_TENANT_SUPPORT, PLATFORM_TENANT_SUPPORT_PERMISSIONS),
            Map.entry(Roles.SERVICE_AGENT, Set.of(
                    Permissions.CLINIC_DOCUMENT_UPLOAD,
                    Permissions.CLINIC_DOCUMENT_AGENT_UPLOAD,
                    Permissions.CLINIC_DOCUMENT_READ,
                    Permissions.AGENT_INTAKE_CREATE,
                    Permissions.AGENT_INTAKE_READ,
                    Permissions.AGENT_INTAKE_SOURCE_READ,
                    Permissions.AGENT_INTAKE_RUN,
                    Permissions.AGENT_INTAKE_RUN_READ,
                    Permissions.CLINIC_EXTRACTION_RUN,
                    Permissions.CLINIC_EXTRACTION_JOBS_READ,
                    Permissions.CLINIC_EXTRACTION_JOBS_MANAGE,
                    Permissions.CLINIC_DASHBOARD_READ
            )),
            Map.entry(Roles.AGENT, Set.of(
                    Permissions.CLINIC_DOCUMENT_AGENT_UPLOAD,
                    Permissions.CLINIC_DOCUMENT_READ,
                    Permissions.AGENT_INTAKE_CREATE,
                    Permissions.AGENT_INTAKE_READ,
                    Permissions.AGENT_INTAKE_SOURCE_READ,
                    Permissions.AGENT_INTAKE_RUN,
                    Permissions.AGENT_INTAKE_RUN_READ,
                    Permissions.CLINIC_EXTRACTION_RUN,
                    Permissions.CLINIC_EXTRACTION_JOBS_READ,
                    Permissions.CLINIC_DASHBOARD_READ
            ))
    );

    private RolePermissionMappings() {}

    public static Set<String> permissionsForRole(String role) {
        if (role == null || role.isBlank()) {
            return Collections.emptySet();
        }
        return ROLE_PERMISSIONS.getOrDefault(normalizeRole(role), Collections.emptySet());
    }

    public static Set<String> permissionsForRoles(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> permissions = new java.util.LinkedHashSet<>();
        for (String role : roles) {
            permissions.addAll(permissionsForRole(role));
        }
        return Collections.unmodifiableSet(permissions);
    }

    public static boolean roleHasPermission(String role, String permission) {
        if (permission == null || permission.isBlank()) {
            return false;
        }
        return permissionsForRole(role).contains(normalizePermission(permission));
    }

    private static String normalizeRole(String role) {
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("ROLE_") ? normalized.substring(5) : normalized;
    }

    private static String normalizePermission(String permission) {
        return permission.trim().toLowerCase(Locale.ROOT);
    }
}
