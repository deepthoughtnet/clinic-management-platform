package com.deepthoughtnet.clinic.api.doctor;

import com.deepthoughtnet.clinic.api.doctor.dto.DoctorProfileRequest;
import com.deepthoughtnet.clinic.api.doctor.dto.DoctorProfileResponse;
import com.deepthoughtnet.clinic.clinic.service.DoctorProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfileRecord;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfileUpsertCommand;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@Validated
@RequestMapping("/api/doctors")
public class DoctorProfileController {
    private final DoctorProfileService doctorProfileService;
    private final TenantUserManagementService tenantUserManagementService;

    public DoctorProfileController(
            DoctorProfileService doctorProfileService,
            TenantUserManagementService tenantUserManagementService
    ) {
        this.doctorProfileService = doctorProfileService;
        this.tenantUserManagementService = tenantUserManagementService;
    }

    @GetMapping("/{doctorUserId}/profile")
    @PreAuthorize("@permissionChecker.hasPermission('user.read') or @permissionChecker.hasPermission('appointment.manage')")
    public DoctorProfileResponse get(@PathVariable UUID doctorUserId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        TenantUserRecord doctor = findDoctor(tenantId, doctorUserId);
        requireProfileReadAccess(doctorUserId);
        DoctorProfileRecord profile = doctorProfileService.findByDoctorUserId(tenantId, doctorUserId).orElse(null);
        return toResponse(doctor, profile);
    }

    @PutMapping("/{doctorUserId}/profile")
    @PreAuthorize("@permissionChecker.hasPermission('appointment.manage')")
    public DoctorProfileResponse update(@PathVariable UUID doctorUserId, @RequestBody DoctorProfileRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        TenantUserRecord doctor = findDoctor(tenantId, doctorUserId);
        String actorRole = normalizedRole(RequestContextHolder.require().tenantRole());
        UUID actorId = RequestContextHolder.require().appUserId();
        DoctorProfileUpsertCommand command = switch (actorRole) {
            case "CLINIC_ADMIN" -> new DoctorProfileUpsertCommand(
                    request.mobile(),
                    request.specialization(),
                    request.qualification(),
                    request.registrationNumber(),
                    request.consultationRoom(),
                    request.active()
            );
            case "RECEPTIONIST" -> new DoctorProfileUpsertCommand(
                    request.mobile(),
                    request.specialization(),
                    null,
                    null,
                    request.consultationRoom(),
                    request.active()
            );
            case "DOCTOR" -> {
                if (!doctorUserId.equals(actorId)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doctors can edit only their own profile");
                }
                yield new DoctorProfileUpsertCommand(
                        request.mobile(),
                        request.specialization(),
                        request.qualification(),
                        request.registrationNumber(),
                        request.consultationRoom(),
                        null
                );
            }
            default -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to update doctor profile");
        };
        DoctorProfileRecord profile = doctorProfileService.upsert(tenantId, doctorUserId, command);
        return toResponse(doctor, profile);
    }

    private TenantUserRecord findDoctor(UUID tenantId, UUID doctorUserId) {
        return tenantUserManagementService.list(tenantId).stream()
                .filter(user -> doctorUserId.equals(user.appUserId()))
                .filter(user -> "DOCTOR".equalsIgnoreCase(user.membershipRole()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Doctor not found for tenant"));
    }

    private void requireProfileReadAccess(UUID doctorUserId) {
        String actorRole = normalizedRole(RequestContextHolder.require().tenantRole());
        UUID actorId = RequestContextHolder.require().appUserId();
        if ("DOCTOR".equals(actorRole) && !doctorUserId.equals(actorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doctors can view only their own profile");
        }
    }

    private DoctorProfileResponse toResponse(TenantUserRecord doctor, DoctorProfileRecord profile) {
        return new DoctorProfileResponse(
                doctor.appUserId() == null ? null : doctor.appUserId().toString(),
                doctor.displayName(),
                doctor.email(),
                doctor.membershipRole(),
                profile == null ? null : profile.mobile(),
                profile == null ? null : profile.specialization(),
                profile == null ? null : profile.qualification(),
                profile == null ? null : profile.registrationNumber(),
                profile == null ? null : profile.consultationRoom(),
                profile == null || profile.active(),
                profile == null ? null : profile.updatedAt()
        );
    }

    private String normalizedRole(String role) {
        if (role == null) return "";
        String normalized = role.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        return normalized.startsWith("ROLE_") ? normalized.substring(5) : normalized;
    }
}
