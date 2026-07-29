package com.deepthoughtnet.clinic.api.discover;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderDocumentType;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderServiceType;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.BrandingCommand;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.CreateProviderApplicationCommand;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ContactVerificationStatusRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.DocumentContentRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ProviderChangeRequestRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ProviderCompletionRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ProviderDashboardRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.LocationCommand;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ProviderApplicationRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ProviderPreviewRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ServiceCommand;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.VerificationChallengeRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.UpdateProviderApplicationCommand;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.UploadedDocumentCommand;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingDocumentNotFoundException;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/provider-registration/providers")
public class ProviderOnboardingController {
    private static final String TOKEN_HEADER = "X-Provider-Onboarding-Token";
    private final ProviderOnboardingService service;

    public ProviderOnboardingController(ProviderOnboardingService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProviderApplicationRecord create(@Valid @RequestBody CreateProviderRequest request) {
        return service.create(new CreateProviderApplicationCommand(
                request.providerType(),
                request.email(),
                request.phone(),
                request.password(),
                request.termsAccepted(),
                request.privacyAccepted()
        ));
    }

    @GetMapping("/me")
    public ProviderApplicationRecord me(@RequestHeader(TOKEN_HEADER) String token) {
        return service.getMe(token);
    }

    @GetMapping("/me/dashboard")
    public ProviderDashboardRecord dashboard(@RequestHeader(TOKEN_HEADER) String token) {
        return service.dashboard(token);
    }

    @GetMapping("/{id}")
    public ProviderApplicationRecord get(@PathVariable UUID id, @RequestHeader(TOKEN_HEADER) String token) {
        return service.get(id, token);
    }

    @GetMapping("/{id}/completion")
    public ProviderCompletionRecord completion(@PathVariable UUID id, @RequestHeader(TOKEN_HEADER) String token) {
        return service.completion(id, token);
    }

    @GetMapping("/{id}/status-history")
    public java.util.List<com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.StatusHistoryRecord> statusHistory(@PathVariable UUID id, @RequestHeader(TOKEN_HEADER) String token) {
        return service.statusHistory(id, token);
    }

    @GetMapping("/{id}/change-requests")
    public java.util.List<ProviderChangeRequestRecord> changeRequests(@PathVariable UUID id, @RequestHeader(TOKEN_HEADER) String token) {
        return service.changeRequests(id, token);
    }

    @PutMapping("/{id}")
    public ProviderApplicationRecord update(@PathVariable UUID id, @RequestHeader(TOKEN_HEADER) String token, @RequestBody UpdateProviderRequest request) {
        return service.update(id, token, request.toCommand());
    }

    @PostMapping("/{id}/submit")
    public ProviderApplicationRecord submit(@PathVariable UUID id, @RequestHeader(TOKEN_HEADER) String token) {
        return service.submit(id, token);
    }

    @PostMapping("/{id}/resubmit")
    public ProviderApplicationRecord resubmit(@PathVariable UUID id, @RequestHeader(TOKEN_HEADER) String token, @RequestBody(required = false) ResubmitRequest request) {
        return service.resubmit(id, token, request == null ? null : request.providerResponseNote());
    }

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object uploadDocument(
            @PathVariable UUID id,
            @RequestHeader(TOKEN_HEADER) String token,
            @RequestParam("documentType") ProviderDocumentType documentType,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        return service.uploadDocument(id, token, new UploadedDocumentCommand(
                documentType,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                file.getBytes()
        ));
    }

    @GetMapping("/{id}/preview")
    public ProviderPreviewRecord preview(@PathVariable UUID id, @RequestHeader(TOKEN_HEADER) String token) {
        return service.preview(id, token);
    }

    @GetMapping("/{id}/contact-verification")
    public ContactVerificationStatusRecord contactVerification(@PathVariable UUID id, @RequestHeader(TOKEN_HEADER) String token) {
        return service.contactVerification(id, token);
    }

    @PostMapping("/{id}/contact-verification/email/request")
    public VerificationChallengeRecord requestEmailVerification(@PathVariable UUID id, @RequestHeader(TOKEN_HEADER) String token) {
        return service.requestEmailVerification(id, token);
    }

    @PostMapping("/{id}/contact-verification/email/verify")
    public ContactVerificationStatusRecord verifyEmail(@PathVariable UUID id, @RequestHeader(TOKEN_HEADER) String token, @RequestBody VerificationCodeRequest request) {
        return service.verifyEmail(id, token, request.code());
    }

    @PostMapping("/{id}/contact-verification/phone/request")
    public VerificationChallengeRecord requestPhoneVerification(@PathVariable UUID id, @RequestHeader(TOKEN_HEADER) String token) {
        return service.requestPhoneVerification(id, token);
    }

    @PostMapping("/{id}/contact-verification/phone/verify")
    public ContactVerificationStatusRecord verifyPhone(@PathVariable UUID id, @RequestHeader(TOKEN_HEADER) String token, @RequestBody VerificationCodeRequest request) {
        return service.verifyPhone(id, token, request.code());
    }

    @GetMapping("/{id}/documents/{documentId}/content")
    public ResponseEntity<byte[]> documentContent(@PathVariable UUID id, @PathVariable UUID documentId, @RequestHeader(TOKEN_HEADER) String token) {
        DocumentContentRecord content;
        try {
            content = service.downloadDocument(id, token, documentId);
        } catch (ProviderOnboardingDocumentNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType() == null || content.contentType().isBlank() ? "application/octet-stream" : content.contentType()))
                .header("Content-Disposition", "inline; filename=\"" + content.originalFilename() + "\"")
                .body(content.bytes());
    }

    @PostMapping("/{id}/review/start")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ADMIN')")
    public ProviderApplicationRecord startReview(@PathVariable UUID id, @RequestBody(required = false) ReviewTransitionRequest request) {
        return service.startReview(id, request == null ? null : request.reason());
    }

    @PostMapping("/{id}/review/changes-requested")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ADMIN')")
    public ProviderApplicationRecord requestChanges(@PathVariable UUID id, @RequestBody ReviewTransitionRequest request) {
        return service.requestChanges(id, request == null ? null : request.reason(), request == null ? null : request.requestedSections());
    }

    @PostMapping("/{id}/review/approve")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','ADMIN')")
    public ProviderApplicationRecord approve(@PathVariable UUID id, @RequestBody(required = false) ReviewTransitionRequest request) {
        return service.approve(id, request == null ? null : request.reason());
    }

    public record CreateProviderRequest(
            @NotNull ProviderType providerType,
            @Email @NotBlank String email,
            @NotBlank String phone,
            @NotBlank @Size(min = 8, max = 128) String password,
            Boolean termsAccepted,
            Boolean privacyAccepted
    ) {
    }

    public record UpdateProviderRequest(
            Long version,
            String email,
            String phone,
            Boolean contactVerified,
            Boolean termsAccepted,
            Boolean privacyAccepted,
            String displayName,
            String legalName,
            String organisationType,
            String registrationNumber,
            String gstNumber,
            String website,
            String gender,
            LocalDate dateOfBirth,
            List<String> languages,
            String biography,
            String medicalCouncil,
            String qualification,
            Integer yearsOfExperience,
            List<String> specialities,
            List<String> subSpecialities,
            BigDecimal consultationFee,
            Boolean onlineConsultation,
            Integer appointmentDurationMinutes,
            String ownership,
            String hospitalType,
            Integer beds,
            Boolean emergencyAvailable,
            String medicalDirector,
            List<String> departments,
            List<String> facilities,
            List<String> accreditations,
            List<LocationRequest> locations,
            List<ServiceRequest> services,
            BrandingRequest branding
    ) {
        UpdateProviderApplicationCommand toCommand() {
            return new UpdateProviderApplicationCommand(
                    version, email, phone, contactVerified, termsAccepted, privacyAccepted, displayName, legalName, organisationType,
                    registrationNumber, gstNumber, website, gender, dateOfBirth, languages, biography, medicalCouncil, qualification,
                    yearsOfExperience, specialities, subSpecialities, consultationFee, onlineConsultation, appointmentDurationMinutes,
                    ownership, hospitalType, beds, emergencyAvailable, medicalDirector, departments, facilities, accreditations,
                    locations == null ? null : locations.stream().map(LocationRequest::toCommand).toList(),
                    services == null ? null : services.stream().map(ServiceRequest::toCommand).toList(),
                    branding == null ? null : branding.toCommand()
            );
        }
    }

    public record ResubmitRequest(String providerResponseNote) {
    }

    public record VerificationCodeRequest(@NotBlank String code) {
    }

    public record LocationRequest(
            UUID id,
            String label,
            String address,
            String city,
            String state,
            String country,
            String pinCode,
            String workingHours,
            Boolean parkingAvailable,
            Boolean accessibilityAvailable,
            @DecimalMin(value = "-90.0", inclusive = true) @DecimalMax(value = "90.0", inclusive = true) java.math.BigDecimal latitude,
            @DecimalMin(value = "-180.0", inclusive = true) @DecimalMax(value = "180.0", inclusive = true) java.math.BigDecimal longitude
    ) {
        LocationCommand toCommand() {
            return new LocationCommand(id, label, address, city, state, country, pinCode, workingHours, parkingAvailable, accessibilityAvailable, latitude, longitude);
        }
    }

    public record ServiceRequest(UUID id, ProviderServiceType serviceType, String label, String description, Boolean enabled) {
        ServiceCommand toCommand() {
            return new ServiceCommand(id, serviceType, label, description, enabled);
        }
    }

    public record BrandingRequest(UUID logoDocumentId, UUID coverImageDocumentId, UUID doctorPhotoDocumentId, String primaryColor, String tagline) {
        BrandingCommand toCommand() {
            return new BrandingCommand(logoDocumentId, coverImageDocumentId, doctorPhotoDocumentId, primaryColor, tagline);
        }
    }

    public record ReviewTransitionRequest(String reason, List<String> requestedSections) {
    }
}
