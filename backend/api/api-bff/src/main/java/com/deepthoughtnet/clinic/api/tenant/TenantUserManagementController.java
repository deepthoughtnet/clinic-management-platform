package com.deepthoughtnet.clinic.api.tenant;

import com.deepthoughtnet.clinic.api.clinic.dto.ClinicUserResponse;
import com.deepthoughtnet.clinic.api.tenant.dto.UpdateTenantUserProfileRequest;
import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.CreateTenantUserCommand;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.identity.service.model.UpdateTenantUserProfileCommand;
import com.deepthoughtnet.clinic.platform.security.Roles;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@Validated
@RequestMapping("/api/tenant/users")
public class TenantUserManagementController {
    private static final Set<String> CLINIC_ASSIGNABLE_ROLES = Set.of(
            "CLINIC_ADMIN",
            "ENGAGE_MANAGER",
            "ENGAGE_EXECUTIVE",
            "DOCTOR",
            "RECEPTIONIST",
            "BILLING_USER",
            "AUDITOR",
            "SERVICE_AGENT",
            "LAB_TECHNICIAN",
            "LAB_ASSISTANT",
            "LAB_FRONT_DESK",
            "PHARMACIST",
            "PHARMACY_INVENTORY_MANAGER",
            "PHARMACY_POS_USER",
            "LAB_APPROVER"
    );
    private static final Set<String> PLATFORM_ASSIGNABLE_TENANT_ROLES = Set.of(
            "TENANT_ADMIN",
            "ADMIN",
            "CLINIC_ADMIN",
            "ENGAGE_MANAGER",
            "ENGAGE_EXECUTIVE",
            "DOCTOR",
            "RECEPTIONIST",
            "BILLING_USER",
            "AUDITOR",
            "SERVICE_AGENT",
            "LAB_TECHNICIAN",
            "LAB_ASSISTANT",
            "LAB_FRONT_DESK",
            "PHARMACIST",
            "PHARMACY_INVENTORY_MANAGER",
            "PHARMACY_POS_USER",
            "LAB_APPROVER"
    );

    private final TenantUserManagementService tenantUserManagementService;
    private final AppointmentService appointmentService;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;

    public TenantUserManagementController(
            TenantUserManagementService tenantUserManagementService,
            AppointmentService appointmentService,
            AuditEventPublisher auditEventPublisher,
            ObjectMapper objectMapper
    ) {
        this.tenantUserManagementService = tenantUserManagementService;
        this.appointmentService = appointmentService;
        this.auditEventPublisher = auditEventPublisher;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasAnyPermission('tenant.users.manage','user.manage')")
    public ClinicUserResponse create(@Valid @RequestBody CreateTenantUserRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        String role = normalizeRole(request.role());
        enforceRoleAssignmentBoundary(role);
        TenantUserRecord created = tenantUserManagementService.createOrInvite(
                new CreateTenantUserCommand(
                        tenantId,
                        request.email(),
                        request.username(),
                        request.firstName(),
                        request.lastName(),
                        joinName(request.firstName(), request.lastName()),
                        role,
                        request.resolvedTemporaryPassword(),
                        request.employeeCode(),
                        request.mobile(),
                        request.department()
                )
        );
        if (!request.active()) {
            created = tenantUserManagementService.updateStatus(tenantId, created.appUserId(), false);
        }
        ensureDoctorCalendarForCurrentRole(tenantId, created.appUserId(), created.membershipRole(), request.active(), "tenant.user.created");
        audit("tenant.user.created", created.appUserId(), "Created tenant user", Map.of(
                "email", created.email(),
                "displayName", created.displayName(),
                "role", created.membershipRole(),
                "active", request.active()
        ));
        return toResponse(created);
    }

    @PutMapping("/{appUserId}")
    @PreAuthorize("@permissionChecker.hasAnyPermission('tenant.users.manage','user.manage')")
    public ClinicUserResponse update(@PathVariable UUID appUserId, @Valid @RequestBody UpdateTenantUserRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        TenantUserRecord record = tenantUserManagementService.updateStatus(tenantId, appUserId, request.active());
        if (StringUtils.hasText(request.role())) {
            String role = normalizeRole(request.role());
            enforceRoleAssignmentBoundary(role);
            record = tenantUserManagementService.updateRole(tenantId, appUserId, role);
            audit("tenant.user.role.updated", record.appUserId(), "Updated tenant user role", Map.of(
                    "email", record.email(),
                    "displayName", record.displayName(),
                    "role", record.membershipRole(),
                    "active", request.active()
            ));
        }
        ensureDoctorCalendarForCurrentRole(tenantId, record.appUserId(), record.membershipRole(), request.active(), "tenant.user.updated");
        audit(request.active() ? "tenant.user.activated" : "tenant.user.deactivated", record.appUserId(), request.active() ? "Activated tenant user" : "Deactivated tenant user", Map.of(
                "email", record.email(),
                "displayName", record.displayName(),
                "role", record.membershipRole(),
                "active", request.active()
        ));
        return toResponse(record);
    }

    @PostMapping("/{appUserId}/roles")
    @PreAuthorize("@permissionChecker.hasAnyPermission('tenant.users.role.assign','tenant.users.manage','user.manage')")
    public ClinicUserResponse assignRole(@PathVariable UUID appUserId, @Valid @RequestBody AssignRoleRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        String role = normalizeRole(request.role());
        enforceRoleAssignmentBoundary(role);
        TenantUserRecord record = tenantUserManagementService.updateRole(tenantId, appUserId, role);
        boolean active = record.membershipStatus() != null && "ACTIVE".equalsIgnoreCase(record.membershipStatus());
        ensureDoctorCalendarForCurrentRole(tenantId, record.appUserId(), record.membershipRole(), active, "tenant.user.role.assigned");
        audit("tenant.user.role.assigned", record.appUserId(), "Assigned tenant user role", Map.of(
                "email", record.email(),
                "displayName", record.displayName(),
                "role", record.membershipRole(),
                "active", active
        ));
        return toResponse(record);
    }

    @PostMapping("/{appUserId}/reset-password")
    @PreAuthorize("@permissionChecker.hasAnyPermission('tenant.users.reset.password','tenant.users.manage','user.manage')")
    public ClinicUserResponse resetPassword(@PathVariable UUID appUserId, @Valid @RequestBody ResetPasswordRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        TenantUserRecord record = tenantUserManagementService.resetPassword(
                tenantId,
                appUserId,
                request.tempPassword(),
                request.temporary()
        );
        audit("tenant.user.password_reset", record.appUserId(), "Reset tenant user password", Map.of(
                "email", record.email(),
                "displayName", record.displayName(),
                "temporary", request.temporary()
        ));
        return toResponse(record);
    }

    @PutMapping("/{appUserId}/profile")
    @PreAuthorize("@permissionChecker.hasAnyPermission('tenant.users.manage','user.manage')")
    public ClinicUserResponse updateProfile(@PathVariable UUID appUserId, @Valid @RequestBody UpdateTenantUserProfileRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        enforceUserEditBoundary(appUserId);
        String normalizedRole = null;
        if (StringUtils.hasText(request.role())) {
            normalizedRole = normalizeRole(request.role());
            enforceRoleAssignmentBoundary(normalizedRole);
        }
        TenantUserRecord record = tenantUserManagementService.updateUserProfile(new UpdateTenantUserProfileCommand(
                tenantId,
                appUserId,
                request.displayName(),
                request.employeeCode(),
                request.mobile(),
                request.department(),
                normalizedRole,
                request.active()
        ));
        ensureDoctorCalendarForCurrentRole(tenantId, record.appUserId(), record.membershipRole(), "ACTIVE".equalsIgnoreCase(record.membershipStatus()), "tenant.user.profile.updated");
        audit("tenant.user.profile.updated", record.appUserId(), "Updated tenant user profile", Map.of(
                "email", record.email(),
                "displayName", record.displayName(),
                "role", record.membershipRole(),
                "active", "ACTIVE".equalsIgnoreCase(record.membershipStatus()),
                "employeeCode", record.employeeCode() == null ? "" : record.employeeCode(),
                "mobile", record.mobile() == null ? "" : record.mobile(),
                "department", record.department() == null ? "" : record.department()
        ));
        return toResponse(record);
    }

    private void enforceRoleAssignmentBoundary(String role) {
        if (isPlatformAdminActor()) {
            if (!PLATFORM_ASSIGNABLE_TENANT_ROLES.contains(role)) {
                throw new IllegalArgumentException("Role not allowed for platform admin in tenant context: " + role);
            }
            return;
        }
        String actorRole = RequestContextHolder.require().tenantRole();
        if (actorRole == null) {
            throw new IllegalArgumentException("Missing tenant role in request context");
        }
        if (!CLINIC_ASSIGNABLE_ROLES.contains(role)) {
            throw new IllegalArgumentException("Role not allowed for clinic admin: " + role);
        }
    }

    private void enforceUserEditBoundary(UUID targetAppUserId) {
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        if (actorAppUserId == null || targetAppUserId == null) {
            return;
        }
        if (isPlatformAdminActor()) {
            return;
        }
        String actorRole = normalizeNullableRole(RequestContextHolder.require().tenantRole());
        if (actorAppUserId.equals(targetAppUserId) && isTenantAdministratorRole(actorRole)) {
            throw new IllegalArgumentException("You cannot edit your own user details on Users & Roles.");
        }
    }

    private boolean isPlatformAdminActor() {
        return RequestContextHolder.require().tokenRoles().stream()
                .map(role -> role == null ? null : role.trim().toUpperCase(Locale.ROOT))
                .anyMatch(Roles.PLATFORM_ADMIN::equals);
    }

    private String normalizeNullableRole(String role) {
        return role == null ? null : role.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isTenantAdministratorRole(String role) {
        return Roles.CLINIC_ADMIN.equals(role) || Roles.TENANT_ADMIN.equals(role) || Roles.ADMIN.equals(role);
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            throw new IllegalArgumentException("role is required");
        }
        return role.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    private String joinName(String firstName, String lastName) {
        String first = StringUtils.hasText(firstName) ? firstName.trim() : "";
        String last = StringUtils.hasText(lastName) ? lastName.trim() : "";
        String joined = (first + " " + last).trim();
        return joined.isEmpty() ? null : joined;
    }

    private ClinicUserResponse toResponse(TenantUserRecord record) {
        return new ClinicUserResponse(
                record.appUserId() == null ? null : record.appUserId().toString(),
                record.tenantId() == null ? null : record.tenantId().toString(),
                record.keycloakSub(),
                record.email(),
                record.username(),
                record.department(),
                record.displayName(),
                record.userStatus(),
                record.membershipRole(),
                record.membershipStatus(),
                record.employeeCode(),
                record.mobile(),
                record.lastLoginAt(),
                record.createdAt(),
                record.updatedAt(),
                record.provisioningStatus()
        );
    }

    public record CreateTenantUserRequest(
            @Email @Size(max = 255)
            String email,
            @Size(max = 255)
            String username,
            @Size(max = 128)
            String firstName,
            @Size(max = 128)
            String lastName,
            @NotBlank
            String role,
            @Size(max = 128)
            String tempPassword,
            @Size(max = 128)
            String temporaryPassword,
            @Size(max = 64)
            String employeeCode,
            @Size(max = 32)
            String mobile,
            @Size(max = 128)
            String department,
            boolean active
    ) {
        public CreateTenantUserRequest(
                String email,
                String username,
                String firstName,
                String lastName,
                String role,
                String tempPassword,
                String temporaryPassword,
                boolean active
        ) {
            this(email, username, firstName, lastName, role, tempPassword, temporaryPassword, null, null, null, active);
        }

        public String resolvedTemporaryPassword() {
            return StringUtils.hasText(temporaryPassword) ? temporaryPassword : tempPassword;
        }
    }

    public record UpdateTenantUserRequest(boolean active, @Size(max = 64) String role) {}

    public record AssignRoleRequest(@NotBlank String role) {}

    public record ResetPasswordRequest(@NotBlank @Size(max = 128) String tempPassword, boolean temporary) {}

    private void ensureDoctorCalendarForCurrentRole(UUID tenantId, UUID appUserId, String role, boolean active, String action) {
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        if ("DOCTOR".equalsIgnoreCase(role)) {
            appointmentService.ensureDoctorCalendarExists(tenantId, appUserId, actorAppUserId, action);
            if (!active) {
                appointmentService.deactivateDoctorCalendar(tenantId, appUserId, actorAppUserId, action + ".inactive");
            }
            return;
        }
        appointmentService.deactivateDoctorCalendar(tenantId, appUserId, actorAppUserId, action + ".non_doctor");
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private void audit(String action, UUID entityId, String summary, Map<String, Object> details) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>(details);
        payload.put("correlationId", RequestContextHolder.require().correlationId());
        payload.put("tenantRole", RequestContextHolder.require().tenantRole());
        auditEventPublisher.record(new AuditEventCommand(
                RequestContextHolder.requireTenantId(),
                "TENANT_USER",
                entityId,
                action,
                RequestContextHolder.require().appUserId(),
                OffsetDateTime.now(),
                summary,
                toJson(payload)
        ));
    }
}
