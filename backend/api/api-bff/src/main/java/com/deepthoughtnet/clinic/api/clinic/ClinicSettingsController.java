package com.deepthoughtnet.clinic.api.clinic;

import com.deepthoughtnet.clinic.api.clinic.dto.ClinicProfileRequest;
import com.deepthoughtnet.clinic.api.clinic.dto.ClinicProfileResponse;
import com.deepthoughtnet.clinic.api.clinic.dto.ClinicRoleResponse;
import com.deepthoughtnet.clinic.api.clinic.dto.ClinicUserResponse;
import com.deepthoughtnet.clinic.clinic.service.ClinicProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileUpsertCommand;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.platform.security.RolePermissionMappings;
import com.deepthoughtnet.clinic.platform.security.Roles;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/clinic")
public class ClinicSettingsController {
    private static final List<String> SUPPORTED_ROLES = List.of(
            Roles.CLINIC_ADMIN,
            Roles.DOCTOR,
            Roles.RECEPTIONIST,
            Roles.BILLING_USER,
            Roles.PHARMACIST,
            Roles.LAB_ASSISTANT,
            Roles.AUDITOR
    );

    private final ClinicProfileService clinicProfileService;
    private final TenantUserManagementService tenantUserManagementService;

    public ClinicSettingsController(
            ClinicProfileService clinicProfileService,
            TenantUserManagementService tenantUserManagementService
    ) {
        this.clinicProfileService = clinicProfileService;
        this.tenantUserManagementService = tenantUserManagementService;
    }

    @GetMapping("/profile")
    @PreAuthorize("@permissionChecker.hasPermission('clinic.read')")
    public ClinicProfileResponse getProfile() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return clinicProfileService.findByTenantId(tenantId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Clinic profile not found"));
    }

    @PutMapping("/profile")
    @PreAuthorize("@permissionChecker.hasPermission('clinic.update')")
    public ClinicProfileResponse updateProfile(@RequestBody ClinicProfileRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        ClinicProfileRecord saved = clinicProfileService.upsert(
                tenantId,
                new ClinicProfileUpsertCommand(
                        request.clinicName(),
                        request.displayName(),
                        request.phone(),
                        request.email(),
                        request.addressLine1(),
                        request.addressLine2(),
                        request.city(),
                        request.state(),
                        request.country(),
                        request.postalCode(),
                        request.registrationNumber(),
                        request.gstNumber(),
                        request.logoDocumentId(),
                        request.active()
                ),
                actorAppUserId
        );
        return toResponse(saved);
    }

    @GetMapping("/users")
    @PreAuthorize("@permissionChecker.hasPermission('user.read')")
    public List<ClinicUserResponse> getUsers() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return tenantUserManagementService.list(tenantId).stream()
                .filter(user -> user.membershipRole() != null && SUPPORTED_ROLES.contains(user.membershipRole().trim().toUpperCase(Locale.ROOT)))
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/roles")
    @PreAuthorize("@permissionChecker.hasPermission('user.read')")
    public List<ClinicRoleResponse> getRoles() {
        return SUPPORTED_ROLES.stream()
                .map(role -> new ClinicRoleResponse(
                        role,
                        labelFor(role),
                        RolePermissionMappings.permissionsForRole(role).stream().sorted().toList()
                ))
                .toList();
    }

    private ClinicProfileResponse toResponse(ClinicProfileRecord record) {
        return new ClinicProfileResponse(
                record.id() == null ? null : record.id().toString(),
                record.tenantId() == null ? null : record.tenantId().toString(),
                record.clinicName(),
                record.displayName(),
                record.phone(),
                record.email(),
                record.addressLine1(),
                record.addressLine2(),
                record.city(),
                record.state(),
                record.country(),
                record.postalCode(),
                record.registrationNumber(),
                record.gstNumber(),
                record.logoDocumentId() == null ? null : record.logoDocumentId().toString(),
                record.active(),
                record.createdAt(),
                record.updatedAt()
        );
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

    private String labelFor(String role) {
        if (role == null || role.isBlank()) {
            return "";
        }
        String normalized = role.toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder builder = new StringBuilder();
        boolean capitalize = true;
        for (char ch : normalized.toCharArray()) {
            if (capitalize && Character.isLetter(ch)) {
                builder.append(Character.toUpperCase(ch));
                capitalize = false;
            } else {
                builder.append(ch);
            }
            if (ch == ' ') {
                capitalize = true;
            }
        }
        return builder.toString();
    }
}
