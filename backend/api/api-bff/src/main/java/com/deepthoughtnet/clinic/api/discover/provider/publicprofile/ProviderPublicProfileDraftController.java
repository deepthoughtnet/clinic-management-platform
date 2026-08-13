package com.deepthoughtnet.clinic.api.discover.provider.publicprofile;

import com.deepthoughtnet.clinic.api.discover.provider.auth.ProviderSessionPrincipal;
import com.deepthoughtnet.clinic.api.discover.provider.publicprofile.ProviderPublicProfileDraftModels.ProviderPublicProfileDraftFieldSourceResponse;
import com.deepthoughtnet.clinic.api.discover.provider.publicprofile.ProviderPublicProfileDraftModels.ProviderPublicProfileDraftMediaUploadResponse;
import com.deepthoughtnet.clinic.api.discover.provider.publicprofile.ProviderPublicProfileDraftModels.ProviderPublicProfileDraftReadinessResponse;
import com.deepthoughtnet.clinic.api.discover.provider.publicprofile.ProviderPublicProfileDraftModels.ProviderPublicProfileDraftResponse;
import com.deepthoughtnet.clinic.api.discover.provider.publicprofile.ProviderPublicProfileDraftModels.ProviderPublicProfileDraftSectionResponse;
import com.deepthoughtnet.clinic.api.discover.provider.publicprofile.ProviderPublicProfileDraftModels.ProviderPublicProfileDraftSectionUpdateRequest;
import com.deepthoughtnet.clinic.api.discover.provider.publicprofile.ProviderPublicProfileDraftModels.ProviderPublicProfileDraftVersionResponse;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.publichospitaldoctorassociation.PublicHospitalDoctorDraftAssociationService;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderProfileDetailRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.ProviderPublicProfileDraftService;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftMediaUploadRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftFieldSourceRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftReadinessRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftSectionRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftWorkspaceRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftVersionRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderDocumentType;
import com.deepthoughtnet.clinic.discover.publicprofile.ProviderPublicProfileService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/provider/public-profiles")
@PreAuthorize("hasRole('PROVIDER')")
public class ProviderPublicProfileDraftController {
    private final ProviderPublicProfileDraftService service;
    private final PublicHospitalDoctorDraftAssociationService hospitalDoctorAssociationService;
    private final ProviderPublicProfileService publicProfileService;

    public ProviderPublicProfileDraftController(
            ProviderPublicProfileDraftService service,
            PublicHospitalDoctorDraftAssociationService hospitalDoctorAssociationService,
            ProviderPublicProfileService publicProfileService
    ) {
        this.service = service;
        this.hospitalDoctorAssociationService = hospitalDoctorAssociationService;
        this.publicProfileService = publicProfileService;
    }

    @PostMapping("/{publicProfileReference}/draft")
    public ResponseEntity<ProviderPublicProfileDraftResponse> createOrLoadDraft(
            Authentication authentication,
            @PathVariable String publicProfileReference
    ) {
        ProviderSessionPrincipal principal = requirePrincipal(authentication);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(toResponse(service.createOrLoadDraft(principal.providerAccountId(), publicProfileReference)));
    }

    @GetMapping("/{publicProfileReference}/draft")
    public ResponseEntity<ProviderPublicProfileDraftResponse> getDraft(
            Authentication authentication,
            @PathVariable String publicProfileReference
    ) {
        ProviderSessionPrincipal principal = requirePrincipal(authentication);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(toResponse(service.getDraft(principal.providerAccountId(), publicProfileReference)));
    }

    @PatchMapping("/{publicProfileReference}/draft/{sectionKey}")
    public ResponseEntity<ProviderPublicProfileDraftResponse> saveDraftSection(
            Authentication authentication,
            @PathVariable String publicProfileReference,
            @PathVariable String sectionKey,
            @RequestBody(required = false) ProviderPublicProfileDraftSectionUpdateRequest request
    ) {
        ProviderSessionPrincipal principal = requirePrincipal(authentication);
        ProviderPublicProfileDraftSectionUpdateRequest payload = request == null
                ? new ProviderPublicProfileDraftSectionUpdateRequest(sectionKey, Map.of(), null, null)
                : new ProviderPublicProfileDraftSectionUpdateRequest(sectionKey, request.content(), request.expectedVersion(), request.changeSummary());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(toResponse(service.saveSection(
                        principal.providerAccountId(),
                        publicProfileReference,
                        new com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftSectionUpdateRequest(
                                payload.sectionKey(),
                                payload.content(),
                                payload.expectedVersion(),
                                payload.changeSummary()
                        ))));
    }

    @GetMapping("/{publicProfileReference}/readiness")
    public ResponseEntity<ProviderPublicProfileDraftReadinessResponse> readiness(
            Authentication authentication,
            @PathVariable String publicProfileReference
    ) {
        ProviderSessionPrincipal principal = requirePrincipal(authentication);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(toResponse(service.getDraft(principal.providerAccountId(), publicProfileReference)).readiness());
    }

    @PostMapping("/{publicProfileReference}/readiness/recalculate")
    public ResponseEntity<ProviderPublicProfileDraftReadinessResponse> recalculateReadiness(
            Authentication authentication,
            @PathVariable String publicProfileReference
    ) {
        ProviderSessionPrincipal principal = requirePrincipal(authentication);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(toResponse(service.recalculateReadiness(principal.providerAccountId(), publicProfileReference)).readiness());
    }

    @GetMapping("/{publicProfileReference}/preview")
    public ResponseEntity<ProviderPublicProfileDraftResponse> preview(
            Authentication authentication,
            @PathVariable String publicProfileReference
    ) {
        ProviderSessionPrincipal principal = requirePrincipal(authentication);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(toResponse(service.preview(principal.providerAccountId(), publicProfileReference)));
    }

    @PostMapping(value = "/{publicProfileReference}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProviderPublicProfileDraftMediaUploadResponse> uploadMedia(
            Authentication authentication,
            @PathVariable String publicProfileReference,
            @RequestParam("mediaType") ProviderDocumentType mediaType,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "altText", required = false) String altText
    ) throws java.io.IOException {
        ProviderSessionPrincipal principal = requirePrincipal(authentication);
        PublicProfileDraftMediaUploadRecord result = service.uploadMedia(
                principal.providerAccountId(),
                publicProfileReference,
                mediaType,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                file.getBytes(),
                altText
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ProviderPublicProfileDraftMediaUploadResponse(result.mediaReference(), toResponse(result.draft())));
    }

    @GetMapping("/{publicProfileReference}/media/{mediaReference}/content")
    public ResponseEntity<byte[]> mediaContent(
            Authentication authentication,
            @PathVariable String publicProfileReference,
            @PathVariable String mediaReference
    ) {
        ProviderSessionPrincipal principal = requirePrincipal(authentication);
        var content = service.downloadMedia(principal.providerAccountId(), publicProfileReference, mediaReference);
        return ResponseEntity.status(HttpStatus.OK)
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(content.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + content.originalFilename() + "\"")
                .body(content.bytes());
    }

    @GetMapping("/{publicProfileReference}/hospital-doctors")
    public ResponseEntity<List<ProviderHospitalDoctorModels.ProviderHospitalDoctorResponse>> listHospitalDoctors(
            Authentication authentication,
            @PathVariable String publicProfileReference
    ) {
        ProviderSessionPrincipal principal = requirePrincipal(authentication);
        requireHospitalProfile(principal.providerAccountId(), publicProfileReference);
        UUID hospitalReference = UUID.fromString(publicProfileReference);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(hospitalDoctorAssociationService.listDraftDoctorReferencesByHospital(hospitalReference).stream()
                        .map(doctorReference -> toHospitalDoctorResponse(hospitalReference, doctorReference))
                        .toList());
    }

    @PostMapping("/{publicProfileReference}/hospital-doctors")
    public ResponseEntity<List<ProviderHospitalDoctorModels.ProviderHospitalDoctorResponse>> addHospitalDoctor(
            Authentication authentication,
            @PathVariable String publicProfileReference,
            @RequestBody ProviderHospitalDoctorModels.ProviderHospitalDoctorUpsertRequest request
    ) {
        ProviderSessionPrincipal principal = requirePrincipal(authentication);
        requireHospitalProfile(principal.providerAccountId(), publicProfileReference);
        UUID hospitalReference = UUID.fromString(publicProfileReference);
        UUID doctorReferenceUuid = parseUuid(request == null ? null : request.publicDoctorReference());
        if (doctorReferenceUuid == null) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Public doctor reference is required.");
        }
        assertPublishedDoctor(doctorReferenceUuid);
        hospitalDoctorAssociationService.listDraftDoctorReferencesByHospital(hospitalReference);
        hospitalDoctorAssociationService.upsertActiveAssociation(
                hospitalReference,
                doctorReferenceUuid,
                PublicHospitalDoctorDraftAssociationService.SOURCE_SYSTEM_DISCOVER_PROVIDER,
                hospitalReference,
                doctorReferenceUuid,
                java.time.OffsetDateTime.now()
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(hospitalDoctorAssociationService.listDraftDoctorReferencesByHospital(hospitalReference).stream()
                        .map(doctorReference -> toHospitalDoctorResponse(hospitalReference, doctorReference))
                        .toList());
    }

    @PostMapping("/{publicProfileReference}/hospital-doctors/{publicDoctorReference}/remove")
    public ResponseEntity<List<ProviderHospitalDoctorModels.ProviderHospitalDoctorResponse>> removeHospitalDoctor(
            Authentication authentication,
            @PathVariable String publicProfileReference,
            @PathVariable String publicDoctorReference
    ) {
        ProviderSessionPrincipal principal = requirePrincipal(authentication);
        requireHospitalProfile(principal.providerAccountId(), publicProfileReference);
        UUID hospitalReference = UUID.fromString(publicProfileReference);
        UUID doctorReferenceUuid = parseUuid(publicDoctorReference);
        if (doctorReferenceUuid == null) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Public doctor reference is required.");
        }
        hospitalDoctorAssociationService.listDraftDoctorReferencesByHospital(hospitalReference);
        hospitalDoctorAssociationService.deactivateAssociation(
                hospitalReference,
                doctorReferenceUuid,
                PublicHospitalDoctorDraftAssociationService.SOURCE_SYSTEM_DISCOVER_PROVIDER,
                hospitalReference,
                doctorReferenceUuid,
                java.time.OffsetDateTime.now()
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(hospitalDoctorAssociationService.listDraftDoctorReferencesByHospital(hospitalReference).stream()
                        .map(doctorReference -> toHospitalDoctorResponse(hospitalReference, doctorReference))
                        .toList());
    }

    private ProviderPublicProfileDraftResponse toResponse(PublicProfileDraftWorkspaceRecord record) {
        return new ProviderPublicProfileDraftResponse(
                record.draftId(),
                record.draftReference(),
                record.publicProfileReference(),
                record.publicProfileType(),
                record.providerAccountId(),
                record.ownershipStatus(),
                record.tenantConsentStatus(),
                record.publicProfileStatus(),
                record.contentStatus(),
                record.readinessStatus(),
                record.completenessPercentage(),
                record.currentVersion(),
                record.createdAt(),
                record.updatedAt(),
                record.lastSavedAt(),
                record.ownershipUpdatedAt(),
                record.displayName(),
                record.canonicalSlug(),
                record.city(),
                record.area(),
                record.state(),
                record.country(),
                record.publicPhone(),
                record.publicEmail(),
                record.website(),
                record.whatsappNumber(),
                record.registrationNumber(),
                record.establishedYear(),
                record.sourceSystem(),
                record.sourceReference(),
                record.sourceRevision(),
                record.sourceUpdatedAt(),
                record.publicProfilePath(),
                record.allowedActions(),
                record.sections().stream().map(this::toSection).toList(),
                toReadiness(record.readiness()),
                record.versions().stream().map(this::toVersion).toList(),
                toFieldSources(record.fieldSources())
        );
    }

    private ProviderPublicProfileDraftSectionResponse toSection(PublicProfileDraftSectionRecord record) {
        return new ProviderPublicProfileDraftSectionResponse(
                record.key(),
                record.title(),
                record.content(),
                toFieldSources(record.sources())
        );
    }

    private ProviderPublicProfileDraftReadinessResponse toReadiness(PublicProfileDraftReadinessRecord record) {
        return new ProviderPublicProfileDraftReadinessResponse(
                record.readinessStatus(),
                record.ready(),
                record.completenessPercentage(),
                record.missingMandatoryFields(),
                record.recommendedFields(),
                record.invalidFields(),
                record.warnings(),
                record.blockingReasons(),
                record.lastEvaluatedAt(),
                record.evaluatedDraftVersion()
        );
    }

    private ProviderPublicProfileDraftVersionResponse toVersion(PublicProfileDraftVersionRecord record) {
        return new ProviderPublicProfileDraftVersionResponse(
                record.id(),
                record.versionNumber(),
                record.changeSummary(),
                record.createdAt(),
                record.createdByProviderAccountId()
        );
    }

    private Map<String, ProviderPublicProfileDraftFieldSourceResponse> toFieldSources(Map<String, PublicProfileDraftFieldSourceRecord> sources) {
        return sources.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                entry -> new ProviderPublicProfileDraftFieldSourceResponse(
                        entry.getValue().sourceSystem(),
                        entry.getValue().sourceReference(),
                        entry.getValue().sourceRevision(),
                        entry.getValue().importedAt(),
                        entry.getValue().lastEditedBy(),
                        entry.getValue().lastEditedAt(),
                        entry.getValue().providerOverride()
                ),
                (left, right) -> left,
                java.util.LinkedHashMap::new
        ));
    }

    private void requireHospitalProfile(UUID providerAccountId, String publicProfileReference) {
        PublicProfileDraftWorkspaceRecord workspace = service.getDraft(providerAccountId, publicProfileReference);
        if (workspace.publicProfileType() != ProviderType.HOSPITAL) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Hospital doctor associations are only available for hospital profiles.");
        }
    }

    private void assertPublishedDoctor(UUID doctorReference) {
        PublicProviderProfileDetailRecord detail = publicProfileService.findByProviderId(doctorReference)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Public doctor profile not found."));
        if (detail.providerType() != ProviderType.INDIVIDUAL_DOCTOR) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Only published doctors can be associated with a hospital.");
        }
    }

    private ProviderHospitalDoctorModels.ProviderHospitalDoctorResponse toHospitalDoctorResponse(UUID hospitalReference, UUID doctorReference) {
        PublicProviderProfileDetailRecord detail = publicProfileService.findByProviderId(doctorReference)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Public doctor profile not found."));
        PublicProviderProfileDetailRecord hospital = publicProfileService.findByProviderId(hospitalReference).orElse(null);
        return new ProviderHospitalDoctorModels.ProviderHospitalDoctorResponse(
                detail.providerId().toString(),
                detail.displayName(),
                detail.primarySpeciality(),
                detail.qualification(),
                detail.referenceNumber(),
                detail.yearsOfExperience(),
                detail.publicPath(),
                "ACTIVE",
                hospital == null ? null : hospital.displayName(),
                hospital == null ? null : hospital.canonicalSlug(),
                detail.languages()
        );
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private ProviderSessionPrincipal requirePrincipal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof ProviderSessionPrincipal principal)) {
            throw new IllegalStateException("provider session is required");
        }
        return principal;
    }
}
