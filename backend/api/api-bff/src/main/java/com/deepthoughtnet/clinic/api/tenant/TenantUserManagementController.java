package com.deepthoughtnet.clinic.api.tenant;

import com.deepthoughtnet.clinic.api.clinic.dto.ClinicUserResponse;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.CreateTenantUserCommand;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenant/users")
public class TenantUserManagementController {
    private static final Set<String> CLINIC_ASSIGNABLE_ROLES = Set.of(
            "CLINIC_ADMIN",
            "DOCTOR",
            "RECEPTIONIST",
            "BILLING_USER",
            "AUDITOR",
            "SERVICE_AGENT",
            "LAB_ASSISTANT",
            "PHARMACIST"
    );

    private final TenantUserManagementService tenantUserManagementService;

    public TenantUserManagementController(TenantUserManagementService tenantUserManagementService) {
        this.tenantUserManagementService = tenantUserManagementService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasAnyPermission('tenant.users.manage','user.manage')")
    public ClinicUserResponse create(@RequestBody CreateTenantUserRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        String role = normalizeRole(request.role());
        enforceRoleAssignmentBoundary(role);
        TenantUserRecord created = tenantUserManagementService.createOrInvite(
                new CreateTenantUserCommand(
                        tenantId,
                        request.email(),
                        request.username(),
                        joinName(request.firstName(), request.lastName()),
                        role,
                        request.resolvedTemporaryPassword()
                )
        );
        if (!request.active()) {
            created = tenantUserManagementService.updateStatus(tenantId, created.appUserId(), false);
        }
        return toResponse(created);
    }

    @PutMapping("/{appUserId}")
    @PreAuthorize("@permissionChecker.hasAnyPermission('tenant.users.manage','user.manage')")
    public ClinicUserResponse update(@PathVariable UUID appUserId, @RequestBody UpdateTenantUserRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        TenantUserRecord record = tenantUserManagementService.updateStatus(tenantId, appUserId, request.active());
        if (StringUtils.hasText(request.role())) {
            String role = normalizeRole(request.role());
            enforceRoleAssignmentBoundary(role);
            record = tenantUserManagementService.updateRole(tenantId, appUserId, role);
        }
        return toResponse(record);
    }

    @PostMapping("/{appUserId}/roles")
    @PreAuthorize("@permissionChecker.hasAnyPermission('tenant.users.role.assign','tenant.users.manage','user.manage')")
    public ClinicUserResponse assignRole(@PathVariable UUID appUserId, @RequestBody AssignRoleRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        String role = normalizeRole(request.role());
        enforceRoleAssignmentBoundary(role);
        return toResponse(tenantUserManagementService.updateRole(tenantId, appUserId, role));
    }

    @PostMapping("/{appUserId}/reset-password")
    @PreAuthorize("@permissionChecker.hasAnyPermission('tenant.users.reset.password','tenant.users.manage','user.manage')")
    public ClinicUserResponse resetPassword(@PathVariable UUID appUserId, @RequestBody ResetPasswordRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return toResponse(tenantUserManagementService.resetPassword(
                tenantId,
                appUserId,
                request.tempPassword(),
                request.temporary()
        ));
    }

    private void enforceRoleAssignmentBoundary(String role) {
        String actorRole = RequestContextHolder.require().tenantRole();
        if (actorRole == null) {
            throw new IllegalArgumentException("Missing tenant role in request context");
        }
        if (!CLINIC_ASSIGNABLE_ROLES.contains(role)) {
            throw new IllegalArgumentException("Role not allowed for clinic admin: " + role);
        }
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
                record.displayName(),
                record.userStatus(),
                record.membershipRole(),
                record.membershipStatus(),
                record.createdAt(),
                record.updatedAt(),
                record.provisioningStatus()
        );
    }

    public record CreateTenantUserRequest(
            String email,
            String username,
            String firstName,
            String lastName,
            String role,
            String tempPassword,
            String temporaryPassword,
            boolean active
    ) {
        public String resolvedTemporaryPassword() {
            return StringUtils.hasText(temporaryPassword) ? temporaryPassword : tempPassword;
        }
    }

    public record UpdateTenantUserRequest(boolean active, String role) {}

    public record AssignRoleRequest(String role) {}

    public record ResetPasswordRequest(String tempPassword, boolean temporary) {}
}
