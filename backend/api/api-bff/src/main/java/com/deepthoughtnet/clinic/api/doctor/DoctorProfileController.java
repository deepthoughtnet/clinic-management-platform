package com.deepthoughtnet.clinic.api.doctor;

import com.deepthoughtnet.clinic.api.doctor.dto.DoctorProfileRequest;
import com.deepthoughtnet.clinic.api.doctor.dto.DoctorProfileResponse;
import com.deepthoughtnet.clinic.clinic.service.DoctorProfileService;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfilePhotoRecord;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfileRecord;
import com.deepthoughtnet.clinic.clinic.service.model.DoctorProfileUpsertCommand;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;

@RestController
@Validated
@RequestMapping("/api/doctors")
public class DoctorProfileController {
    private static final Logger log = LoggerFactory.getLogger(DoctorProfileController.class);

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
        DoctorProfileRecord profile = doctorProfileService.findByDoctorUserIdWithPhotoRepair(tenantId, doctorUserId).orElse(null);
        return toResponse(doctor, profile);
    }

    @GetMapping("/{doctorUserId}/photo")
    @PreAuthorize("@permissionChecker.hasPermission('user.read') or @permissionChecker.hasPermission('appointment.manage')")
    public ResponseEntity<byte[]> photo(@PathVariable UUID doctorUserId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        findDoctor(tenantId, doctorUserId);
        requireProfileReadAccess(doctorUserId);
        DoctorProfilePhotoRecord photo = doctorProfileService.downloadPhoto(tenantId, doctorUserId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .contentLength(photo.sizeBytes())
                .cacheControl(CacheControl.noCache().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + safeFilename(photo.fileName()) + "\"")
                .body(photo.bytes());
    }

    @PutMapping(value = "/{doctorUserId}/profile", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@permissionChecker.hasPermission('appointment.manage')")
    public DoctorProfileResponse update(@PathVariable UUID doctorUserId, @Valid @RequestBody DoctorProfileRequest request) throws Exception {
        return updateInternal(doctorUserId, request, null);
    }

    @PutMapping(value = "/{doctorUserId}/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permissionChecker.hasPermission('appointment.manage')")
    public DoctorProfileResponse updateMultipart(
            @PathVariable UUID doctorUserId,
            @Valid @RequestPart("doctor") DoctorProfileRequest request,
            @RequestPart(value = "photo", required = false) MultipartFile photo
    ) throws Exception {
        return updateInternal(doctorUserId, request, photo);
    }

    @PostMapping(value = "/{doctorUserId}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permissionChecker.hasPermission('appointment.manage')")
    public DoctorProfileResponse uploadPhoto(
            @PathVariable UUID doctorUserId,
            @RequestParam("file") MultipartFile file
    ) throws Exception {
        UUID tenantId = RequestContextHolder.requireTenantId();
        TenantUserRecord doctor = findDoctor(tenantId, doctorUserId);
        String actorRole = normalizedRole(RequestContextHolder.require().tenantRole());
        UUID actorId = RequestContextHolder.require().appUserId();
        log.info(
                "doctor.profile.photo.controller.started tenantId={} doctorUserId={} actorRole={} fileName={} contentType={} size={}",
                tenantId,
                doctorUserId,
                actorRole,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize()
        );
        if ("DOCTOR".equals(actorRole) && !doctorUserId.equals(actorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doctors can edit only their own profile");
        }
        if (!isPhotoContentType(file.getContentType(), file.getOriginalFilename())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Doctor profile photo must be JPG, PNG, or WEBP.");
        }
        DoctorProfileRecord profile = doctorProfileService.updatePhoto(
                tenantId,
                doctorUserId,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getBytes()
        );
        log.info("doctor.profile.photo.controller.completed tenantId={} doctorUserId={}", tenantId, doctorUserId);
        return toResponse(doctor, profile);
    }

    private TenantUserRecord findDoctor(UUID tenantId, UUID doctorUserId) {
        return tenantUserManagementService.list(tenantId).stream()
                .filter(user -> doctorUserId.equals(user.appUserId()))
                .filter(user -> "DOCTOR".equalsIgnoreCase(user.membershipRole()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor profile not found for this clinic."));
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
                profile == null ? List.of() : profile.specializations(),
                profile == null ? null : profile.qualification(),
                profile == null ? null : profile.registrationNumber(),
                profile == null ? null : profile.consultationRoom(),
                profile == null ? null : profile.consultationFee(),
                profile == null ? null : profile.opdFee(),
                profile == null ? null : profile.followUpFee(),
                profile == null ? null : profile.emergencyFee(),
                profile == null ? null : profile.yearsOfExperience(),
                profile == null ? null : profile.age(),
                profile == null || profile.active(),
                profile != null && profile.publicListingEnabled(),
                profile == null ? null : profile.slug(),
                profile == null ? null : profile.photoUrl(),
                profile == null ? null : profile.photoFileName(),
                profile == null ? null : profile.photoMimeType(),
                profile == null ? null : profile.photoSizeBytes(),
                profile == null ? null : profile.updatedAt()
        );
    }

    private DoctorProfileResponse updateInternal(UUID doctorUserId, DoctorProfileRequest request, MultipartFile photo) throws Exception {
        UUID tenantId = RequestContextHolder.requireTenantId();
        TenantUserRecord doctor = findDoctor(tenantId, doctorUserId);
        String actorRole = normalizedRole(RequestContextHolder.require().tenantRole());
        UUID actorId = RequestContextHolder.require().appUserId();
        log.info(
                "doctor.profile.update.started tenantId={} doctorUserId={} actorRole={} hasPhoto={}",
                tenantId,
                doctorUserId,
                actorRole,
                photo != null && !photo.isEmpty()
        );
        DoctorProfileUpsertCommand command = switch (actorRole) {
            case "CLINIC_ADMIN" -> new DoctorProfileUpsertCommand(
                    request.mobile(),
                    request.specialization(),
                    request.specializations(),
                    request.qualification(),
                    request.registrationNumber(),
                    request.consultationRoom(),
                    request.consultationFee(),
                    request.opdFee(),
                    request.followUpFee(),
                    request.emergencyFee(),
                    request.yearsOfExperience(),
                    request.age(),
                    request.active(),
                    request.publicListingEnabled(),
                    request.slug()
            );
            case "RECEPTIONIST" -> new DoctorProfileUpsertCommand(
                    request.mobile(),
                    request.specialization(),
                    request.specializations(),
                    null,
                    null,
                    request.consultationRoom(),
                    request.consultationFee(),
                    request.opdFee(),
                    null,
                    null,
                    request.yearsOfExperience(),
                    request.age(),
                    request.active(),
                    null,
                    null
            );
            case "DOCTOR" -> {
                if (!doctorUserId.equals(actorId)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doctors can edit only their own profile");
                }
                yield new DoctorProfileUpsertCommand(
                        request.mobile(),
                        request.specialization(),
                        request.specializations(),
                        request.qualification(),
                        request.registrationNumber(),
                        request.consultationRoom(),
                        request.consultationFee(),
                        request.opdFee(),
                        request.followUpFee(),
                        request.emergencyFee(),
                        request.yearsOfExperience(),
                        request.age(),
                        null,
                        request.publicListingEnabled(),
                        request.slug()
                );
            }
            default -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to update doctor profile");
        };
        if (photo != null && !photo.isEmpty() && !isPhotoContentType(photo.getContentType(), photo.getOriginalFilename())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Doctor profile photo must be JPG, PNG, or WEBP.");
        }
        DoctorProfileRecord profile = doctorProfileService.upsert(tenantId, doctorUserId, command);
        if (photo != null && !photo.isEmpty()) {
            log.info(
                    "doctor.profile.photo.controller.multipart-upload.started tenantId={} doctorUserId={} fileName={} contentType={} size={}",
                    tenantId,
                    doctorUserId,
                    photo.getOriginalFilename(),
                    photo.getContentType(),
                    photo.getSize()
            );
            profile = doctorProfileService.updatePhoto(
                    tenantId,
                    doctorUserId,
                    photo.getOriginalFilename(),
                    photo.getContentType(),
                    photo.getBytes()
            );
            log.info("doctor.profile.photo.controller.multipart-upload.completed tenantId={} doctorUserId={}", tenantId, doctorUserId);
        }
        log.info("doctor.profile.update.completed tenantId={} doctorUserId={}", tenantId, doctorUserId);
        return toResponse(doctor, profile);
    }

    private String normalizedRole(String role) {
        if (role == null) return "";
        String normalized = role.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        return normalized.startsWith("ROLE_") ? normalized.substring(5) : normalized;
    }

    private boolean isPhotoContentType(String contentType, String originalFilename) {
        String normalized = contentType == null ? null : contentType.trim().toLowerCase(Locale.ROOT);
        if ("image/jpg".equals(normalized)) {
            normalized = "image/jpeg";
        }
        if ("image/jpeg".equals(normalized) || "image/png".equals(normalized) || "image/webp".equals(normalized)) {
            return true;
        }
        if (originalFilename == null) {
            return false;
        }
        String lower = originalFilename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp");
    }

    private String safeFilename(String value) {
        if (value == null || value.isBlank()) {
            return "doctor-photo";
        }
        return value.replace("\"", "");
    }
}
