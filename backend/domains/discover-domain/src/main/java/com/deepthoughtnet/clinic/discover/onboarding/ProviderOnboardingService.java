package com.deepthoughtnet.clinic.discover.onboarding;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderDocumentType;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderLifecycleStatus;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.BrandingCommand;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.BrandingRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.CreateProviderApplicationCommand;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.DocumentRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.LocationCommand;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.LocationRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ProviderApplicationRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ProviderChangeRequestRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ProviderCompletionRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ProviderDashboardRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ProviderPreviewRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ProviderTimelineEventRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ServiceCommand;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ServiceRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.StatusHistoryRecord;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.UpdateProviderApplicationCommand;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.UploadedDocumentCommand;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderApplicationEntity;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderApplicationRepository;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderChangeRequestEntity;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderChangeRequestRepository;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderDocumentEntity;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderDocumentRepository;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderLocationEntity;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderLocationRepository;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderServiceEntity;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderServiceRepository;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderStatusHistoryEntity;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderStatusHistoryRepository;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderSubmissionEntity;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderSubmissionRepository;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ProviderOnboardingService {
    private static final int MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
    private static final List<String> SUPPORTED_CONTENT_TYPES = List.of("image/png", "image/jpeg", "application/pdf");
    private static final List<ProviderLifecycleStatus> READ_ONLY_STATUSES = List.of(
            ProviderLifecycleStatus.SUBMITTED,
            ProviderLifecycleStatus.UNDER_REVIEW,
            ProviderLifecycleStatus.APPROVED,
            ProviderLifecycleStatus.PUBLISHED,
            ProviderLifecycleStatus.SUSPENDED,
            ProviderLifecycleStatus.ARCHIVED
    );
    private final SecureRandom random = new SecureRandom();
    private final ProviderApplicationRepository applications;
    private final ProviderLocationRepository locations;
    private final ProviderServiceRepository services;
    private final ProviderDocumentRepository documents;
    private final ProviderSubmissionRepository submissions;
    private final ProviderStatusHistoryRepository history;
    private final ProviderChangeRequestRepository changeRequests;
    private final ObjectStorageService storageService;
    private final ObjectMapper objectMapper;

    public ProviderOnboardingService(
            ProviderApplicationRepository applications,
            ProviderLocationRepository locations,
            ProviderServiceRepository services,
            ProviderDocumentRepository documents,
            ProviderSubmissionRepository submissions,
            ProviderStatusHistoryRepository history,
            ProviderChangeRequestRepository changeRequests,
            ObjectStorageService storageService,
            ObjectMapper objectMapper
    ) {
        this.applications = applications;
        this.locations = locations;
        this.services = services;
        this.documents = documents;
        this.submissions = submissions;
        this.history = history;
        this.changeRequests = changeRequests;
        this.storageService = storageService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ProviderApplicationRecord create(CreateProviderApplicationCommand command) {
        require(command.providerType() != null, "providerType is required");
        requireText(command.email(), "email");
        requireText(command.phone(), "phone");
        requireText(command.password(), "password");
        require(Boolean.TRUE.equals(command.termsAccepted()), "terms must be accepted");
        require(Boolean.TRUE.equals(command.privacyAccepted()), "privacy must be accepted");

        UUID id = UUID.randomUUID();
        String token = newToken();
        ProviderApplicationEntity entity = ProviderApplicationEntity.create(
                id,
                "JDN-" + OffsetDateTime.now().getYear() + "-" + id.toString().substring(0, 8).toUpperCase(Locale.ROOT),
                command.providerType(),
                digest(token),
                normalize(command.email()),
                normalize(command.phone()),
                digest(command.password()),
                true,
                true
        );
        ProviderCompletionRecord completion = calculateCompletion(entity, List.of(), List.of(), List.of());
        entity.touch(completion.completionPercentage(), completion.currentStep());
        applications.save(entity);
        history.save(new ProviderStatusHistoryEntity(entity.getId(), null, entity.getStatus(), "Draft created"));
        return toRecord(entity, token);
    }

    @Transactional(readOnly = true)
    public ProviderApplicationRecord getMe(String token) {
        return toRecord(requireByToken(token), null);
    }

    @Transactional(readOnly = true)
    public ProviderApplicationRecord get(UUID id, String token) {
        ProviderApplicationEntity entity = requireById(id);
        requireOwns(entity, token);
        return toRecord(entity, null);
    }

    @Transactional(readOnly = true)
    public ProviderDashboardRecord dashboard(String token) {
        ProviderApplicationEntity entity = requireByToken(token);
        return buildDashboard(entity);
    }

    @Transactional(readOnly = true)
    public ProviderCompletionRecord completion(UUID id, String token) {
        ProviderApplicationEntity entity = requireById(id);
        requireOwns(entity, token);
        return calculateCompletion(entity, currentLocations(entity.getId()), currentServices(entity.getId()), documents.findByProviderIdOrderByUploadedAtDesc(entity.getId()));
    }

    @Transactional(readOnly = true)
    public List<StatusHistoryRecord> statusHistory(UUID id, String token) {
        ProviderApplicationEntity entity = requireById(id);
        requireOwns(entity, token);
        return history.findByProviderIdOrderByCreatedAtAsc(entity.getId()).stream().map(this::toHistory).toList();
    }

    @Transactional(readOnly = true)
    public List<ProviderChangeRequestRecord> changeRequests(UUID id, String token) {
        ProviderApplicationEntity entity = requireById(id);
        requireOwns(entity, token);
        return changeRequests.findByProviderIdOrderByRequestedAtDesc(entity.getId()).stream().map(this::toChangeRequest).toList();
    }

    @Transactional
    public ProviderApplicationRecord update(UUID id, String token, UpdateProviderApplicationCommand command) {
        ProviderApplicationEntity entity = requireById(id);
        requireOwns(entity, token);
        ensureEditable(entity);
        verifyVersion(command.version(), entity);

        apply(entity, command);
        if (command.locations() != null) {
            locations.deleteByProviderId(entity.getId());
            locations.saveAll(command.locations().stream().map(item -> toEntity(entity.getId(), item)).toList());
        }
        if (command.services() != null) {
            services.deleteByProviderId(entity.getId());
            services.saveAll(command.services().stream().map(item -> toEntity(entity.getId(), item)).toList());
        }

        ProviderCompletionRecord completion = calculateCompletion(entity, currentLocations(entity.getId()), currentServices(entity.getId()), documents.findByProviderIdOrderByUploadedAtDesc(entity.getId()));
        entity.touch(completion.completionPercentage(), completion.currentStep());
        ProviderLifecycleStatus nextStatus = deriveProviderStatusAfterUpdate(entity, completion);
        if (nextStatus != entity.getStatus()) {
            transition(entity, nextStatus, statusReason(nextStatus));
        }
        applications.save(entity);
        return toRecord(entity, null);
    }

    @Transactional
    public DocumentRecord uploadDocument(UUID id, String token, UploadedDocumentCommand command) {
        ProviderApplicationEntity entity = requireById(id);
        requireOwns(entity, token);
        ensureEditable(entity);
        require(command.documentType() != null, "documentType is required");
        requireText(command.originalFilename(), "filename is required");
        require(SUPPORTED_CONTENT_TYPES.contains(command.contentType()), "Only PNG, JPEG, and PDF uploads are supported");
        require(command.bytes() != null && command.bytes().length > 0, "file is required");
        require(command.sizeBytes() <= MAX_UPLOAD_BYTES, "file must be 5 MB or smaller");

        String storageKey = storageService.buildDocumentStorageKey(
                UUID.nameUUIDFromBytes(entity.getId().toString().getBytes(StandardCharsets.UTF_8)),
                "discover-onboarding/" + command.originalFilename()
        );
        storageService.putObject(storageKey, command.contentType(), command.bytes());
        ProviderDocumentEntity document = documents.save(new ProviderDocumentEntity(
                entity.getId(),
                command.documentType(),
                normalize(command.originalFilename()),
                command.contentType(),
                command.sizeBytes(),
                storageKey
        ));
        attachBrandingReference(entity, document);
        ProviderCompletionRecord completion = calculateCompletion(entity, currentLocations(entity.getId()), currentServices(entity.getId()), documents.findByProviderIdOrderByUploadedAtDesc(entity.getId()));
        entity.touch(completion.completionPercentage(), completion.currentStep());
        applications.save(entity);
        return toDocument(document);
    }

    @Transactional(readOnly = true)
    public ProviderPreviewRecord preview(UUID id, String token) {
        ProviderApplicationEntity entity = requireById(id);
        requireOwns(entity, token);
        List<ProviderLocationEntity> locationRecords = currentLocations(entity.getId());
        List<ProviderServiceEntity> serviceRecords = currentServices(entity.getId());
        List<String> missing = missingItems(entity, locationRecords, serviceRecords, documents.findByProviderIdOrderByUploadedAtDesc(entity.getId()));
        return new ProviderPreviewRecord(
                entity.getId(),
                entity.getProviderType(),
                firstText(entity.getDisplayName(), entity.getLegalName(), entity.getEmail()),
                subtitle(entity),
                locationSummary(locationRecords),
                serviceRecords.stream().filter(ProviderServiceEntity::isEnabled).map(ProviderServiceEntity::getLabel).toList(),
                split(entity.getSpecialities()),
                entity.getBiography(),
                branding(entity),
                entity.getCompletionPercent(),
                missing.isEmpty(),
                missing
        );
    }

    @Transactional
    public ProviderApplicationRecord submit(UUID id, String token) {
        return submitInternal(id, token, null);
    }

    @Transactional
    public ProviderApplicationRecord resubmit(UUID id, String token, String responseNote) {
        return submitInternal(id, token, responseNote);
    }

    @Transactional
    public ProviderApplicationRecord startReview(UUID id, String reason) {
        ProviderApplicationEntity entity = requireById(id);
        require(entity.getStatus() == ProviderLifecycleStatus.SUBMITTED, "provider application must be submitted before review starts");
        transition(entity, ProviderLifecycleStatus.UNDER_REVIEW, firstText(reason, "Verification review started"));
        applications.save(entity);
        return toRecord(entity, null);
    }

    @Transactional
    public ProviderApplicationRecord requestChanges(UUID id, String reason, List<String> requestedSections) {
        ProviderApplicationEntity entity = requireById(id);
        require(entity.getStatus() == ProviderLifecycleStatus.UNDER_REVIEW || entity.getStatus() == ProviderLifecycleStatus.SUBMITTED, "provider application must be submitted or under review before requesting changes");
        ProviderSubmissionEntity latestSubmission = submissions.findFirstByProviderIdOrderByVersionNumberDesc(entity.getId()).orElse(null);
        changeRequests.save(new ProviderChangeRequestEntity(
                entity.getId(),
                latestSubmission == null ? null : latestSubmission.getVersionNumber(),
                requestedSections == null || requestedSections.isEmpty() ? null : String.join(",", requestedSections),
                firstText(reason, "Changes requested by verification team")
        ));
        transition(entity, ProviderLifecycleStatus.CHANGES_REQUESTED, firstText(reason, "Changes requested by verification team"));
        applications.save(entity);
        return toRecord(entity, null);
    }

    @Transactional
    public ProviderApplicationRecord approve(UUID id, String reason) {
        ProviderApplicationEntity entity = requireById(id);
        require(entity.getStatus() == ProviderLifecycleStatus.UNDER_REVIEW, "provider application must be under review before approval");
        transition(entity, ProviderLifecycleStatus.APPROVED, firstText(reason, "Approved for publication readiness"));
        applications.save(entity);
        return toRecord(entity, null);
    }

    private ProviderApplicationRecord submitInternal(UUID id, String token, String responseNote) {
        ProviderApplicationEntity entity = requireById(id);
        requireOwns(entity, token);
        ensureEditableForSubmit(entity);

        List<ProviderLocationEntity> locationRecords = currentLocations(entity.getId());
        List<ProviderServiceEntity> serviceRecords = currentServices(entity.getId());
        List<ProviderDocumentEntity> documentRecords = documents.findByProviderIdOrderByUploadedAtDesc(entity.getId());
        ProviderCompletionRecord completion = calculateCompletion(entity, locationRecords, serviceRecords, documentRecords);
        if (!completion.canSubmit()) {
            throw new IllegalStateException("Cannot submit until required fields are complete: " + String.join(", ", completion.blockingErrors()));
        }

        String snapshotJson = snapshotJson(entity, locationRecords, serviceRecords, documentRecords);
        String snapshotHash = digest(snapshotJson);
        Optional<ProviderSubmissionEntity> existingSubmission = submissions.findFirstByProviderIdAndSnapshotHashOrderByVersionNumberDesc(entity.getId(), snapshotHash);
        if (existingSubmission.isPresent()) {
            resolveOpenChangeRequests(entity.getId(), responseNote);
            return toRecord(entity, null);
        }

        int nextVersion = submissions.findFirstByProviderIdOrderByVersionNumberDesc(entity.getId()).map(ProviderSubmissionEntity::getVersionNumber).orElse(0) + 1;
        ProviderLifecycleStatus before = entity.getStatus();
        entity.markSubmitted();
        submissions.save(new ProviderSubmissionEntity(
                entity.getId(),
                nextVersion,
                before.name(),
                ProviderLifecycleStatus.SUBMITTED.name(),
                "PROVIDER",
                snapshotHash,
                snapshotJson,
                responseNote
        ));
        resolveOpenChangeRequests(entity.getId(), responseNote);
        history.save(new ProviderStatusHistoryEntity(entity.getId(), before, ProviderLifecycleStatus.SUBMITTED, before == ProviderLifecycleStatus.CHANGES_REQUESTED ? "Resubmitted for verification" : "Submitted for verification"));
        applications.save(entity);
        return toRecord(entity, null);
    }

    private void apply(ProviderApplicationEntity entity, UpdateProviderApplicationCommand command) {
        if (command == null) {
            return;
        }
        if (StringUtils.hasText(command.email())) entity.setEmail(normalize(command.email()));
        if (StringUtils.hasText(command.phone())) entity.setPhone(normalize(command.phone()));
        if (command.contactVerified() != null) entity.setContactVerified(command.contactVerified());
        if (command.termsAccepted() != null) entity.setTermsAccepted(command.termsAccepted());
        if (command.privacyAccepted() != null) entity.setPrivacyAccepted(command.privacyAccepted());
        entity.setDisplayName(normalizeNullable(command.displayName()));
        entity.setLegalName(normalizeNullable(command.legalName()));
        entity.setOrganisationType(normalizeNullable(command.organisationType()));
        entity.setRegistrationNumber(normalizeNullable(command.registrationNumber()));
        entity.setGstNumber(normalizeNullable(command.gstNumber()));
        entity.setWebsite(normalizeNullable(command.website()));
        entity.setGender(normalizeNullable(command.gender()));
        entity.setDateOfBirth(command.dateOfBirth());
        entity.setLanguages(join(command.languages()));
        entity.setBiography(normalizeNullable(command.biography()));
        entity.setMedicalCouncil(normalizeNullable(command.medicalCouncil()));
        entity.setQualification(normalizeNullable(command.qualification()));
        entity.setYearsOfExperience(command.yearsOfExperience());
        entity.setSpecialities(join(command.specialities()));
        entity.setSubSpecialities(join(command.subSpecialities()));
        entity.setConsultationFee(command.consultationFee());
        if (command.onlineConsultation() != null) entity.setOnlineConsultation(command.onlineConsultation());
        entity.setAppointmentDurationMinutes(command.appointmentDurationMinutes());
        entity.setOwnership(normalizeNullable(command.ownership()));
        entity.setHospitalType(normalizeNullable(command.hospitalType()));
        entity.setBeds(command.beds());
        if (command.emergencyAvailable() != null) entity.setEmergencyAvailable(command.emergencyAvailable());
        entity.setMedicalDirector(normalizeNullable(command.medicalDirector()));
        entity.setDepartments(join(command.departments()));
        entity.setFacilities(join(command.facilities()));
        entity.setAccreditations(join(command.accreditations()));
        BrandingCommand branding = command.branding();
        if (branding != null) {
            entity.setLogoDocumentId(branding.logoDocumentId());
            entity.setCoverImageDocumentId(branding.coverImageDocumentId());
            entity.setDoctorPhotoDocumentId(branding.doctorPhotoDocumentId());
            entity.setPrimaryColor(normalizeNullable(branding.primaryColor()));
            entity.setTagline(normalizeNullable(branding.tagline()));
        }
    }

    private ProviderCompletionRecord calculateCompletion(ProviderApplicationEntity entity, List<ProviderLocationEntity> locationRecords, List<ProviderServiceEntity> serviceRecords, List<ProviderDocumentEntity> documentRecords) {
        List<String> missingFields = missingFieldCodes(entity, locationRecords, serviceRecords);
        List<String> missingDocuments = missingDocumentCodes(entity, documentRecords);
        boolean accountComplete = StringUtils.hasText(entity.getEmail())
                && StringUtils.hasText(entity.getPhone())
                && entity.isContactVerified()
                && entity.isTermsAccepted()
                && entity.isPrivacyAccepted();
        boolean profileComplete = StringUtils.hasText(firstText(entity.getDisplayName(), entity.getLegalName()))
                && (entity.getProviderType() != ProviderType.INDIVIDUAL_DOCTOR || StringUtils.hasText(entity.getBiography()));
        boolean detailsComplete = switch (entity.getProviderType()) {
            case INDIVIDUAL_DOCTOR -> StringUtils.hasText(entity.getQualification())
                    && StringUtils.hasText(entity.getMedicalCouncil())
                    && StringUtils.hasText(entity.getSpecialities())
                    && entity.getYearsOfExperience() != null;
            case CLINIC -> StringUtils.hasText(entity.getOrganisationType())
                    && StringUtils.hasText(entity.getRegistrationNumber())
                    && StringUtils.hasText(entity.getFacilities());
            case HOSPITAL -> StringUtils.hasText(entity.getOwnership())
                    && StringUtils.hasText(entity.getHospitalType())
                    && entity.getBeds() != null && entity.getBeds() > 0
                    && StringUtils.hasText(entity.getDepartments())
                    && StringUtils.hasText(entity.getMedicalDirector())
                    && entity.isEmergencyAvailable();
        };
        boolean servicesComplete = serviceRecords.stream().anyMatch(ProviderServiceEntity::isEnabled);
        boolean locationsComplete = !locationRecords.isEmpty();
        boolean brandingComplete = switch (entity.getProviderType()) {
            case INDIVIDUAL_DOCTOR -> entity.getDoctorPhotoDocumentId() != null;
            case CLINIC, HOSPITAL -> entity.getLogoDocumentId() != null;
        };
        boolean documentsComplete = missingDocuments.isEmpty();
        boolean previewReady = accountComplete && profileComplete && detailsComplete && servicesComplete && locationsComplete && brandingComplete && documentsComplete;
        boolean canSubmit = previewReady && missingFields.isEmpty() && missingDocuments.isEmpty() && !isReadOnly(entity.getStatus());

        List<StepState> steps = List.of(
                new StepState("ACCOUNT", "Account and contact", accountComplete),
                new StepState("PROFILE", "Provider profile", profileComplete),
                new StepState(entity.getProviderType() == ProviderType.INDIVIDUAL_DOCTOR ? "PROFESSIONAL_DETAILS" : "ORGANISATION_DETAILS", entity.getProviderType() == ProviderType.INDIVIDUAL_DOCTOR ? "Professional details" : "Organisation details", detailsComplete),
                new StepState("SERVICES", "Services and specialities", servicesComplete),
                new StepState("LOCATIONS", "Locations and working hours", locationsComplete),
                new StepState("BRANDING", "Branding and media", brandingComplete),
                new StepState("DOCUMENTS", "Verification documents", documentsComplete),
                new StepState("PREVIEW", "Profile preview", previewReady),
                new StepState("REVIEW", "Review and submit", canSubmit)
        );

        List<String> completedSteps = steps.stream().filter(StepState::complete).map(StepState::label).toList();
        List<String> incompleteSteps = steps.stream().filter(step -> !step.complete()).map(StepState::label).toList();
        String recommendedNextStep = incompleteSteps.isEmpty() ? "Review and submit" : incompleteSteps.get(0);
        int completionPercent = (int) Math.round((completedSteps.size() * 100.0) / steps.size());
        List<String> blockingErrors = new ArrayList<>(missingFields);
        blockingErrors.addAll(missingDocuments);
        List<String> warnings = new ArrayList<>();
        if (!entity.isContactVerified()) {
            warnings.add("CONTACT_VERIFICATION_PENDING");
        }
        if (StringUtils.hasText(entity.getWebsite()) == false) {
            warnings.add("WEBSITE_OPTIONAL");
        }

        return new ProviderCompletionRecord(
                completionPercent,
                completedSteps,
                incompleteSteps,
                missingFields,
                missingDocuments,
                warnings,
                blockingErrors,
                canSubmit,
                recommendedNextStep,
                entity.getCurrentStep(),
                isReadOnly(entity.getStatus())
        );
    }

    private List<String> missingFieldCodes(ProviderApplicationEntity entity, List<ProviderLocationEntity> locationRecords, List<ProviderServiceEntity> serviceRecords) {
        Set<String> missing = new LinkedHashSet<>();
        if (!StringUtils.hasText(entity.getEmail())) missing.add("EMAIL_REQUIRED");
        if (!StringUtils.hasText(entity.getPhone())) missing.add("PHONE_REQUIRED");
        if (!entity.isContactVerified()) missing.add("CONTACT_VERIFICATION_REQUIRED");
        if (!entity.isTermsAccepted()) missing.add("TERMS_ACCEPTANCE_REQUIRED");
        if (!entity.isPrivacyAccepted()) missing.add("PRIVACY_ACCEPTANCE_REQUIRED");
        if (!StringUtils.hasText(firstText(entity.getDisplayName(), entity.getLegalName()))) missing.add(providerNameRequiredCode(entity.getProviderType()));
        if (!StringUtils.hasText(entity.getRegistrationNumber())) missing.add(registrationRequiredCode(entity.getProviderType()));
        if (locationRecords.isEmpty()) missing.add("PRIMARY_LOCATION_REQUIRED");
        if (!serviceRecords.stream().anyMatch(ProviderServiceEntity::isEnabled)) missing.add("SERVICES_REQUIRED");
        if (entity.getProviderType() == ProviderType.INDIVIDUAL_DOCTOR) {
            if (!StringUtils.hasText(entity.getQualification())) missing.add("DOCTOR_QUALIFICATION_REQUIRED");
            if (!StringUtils.hasText(entity.getMedicalCouncil())) missing.add("DOCTOR_REGISTRATION_COUNCIL_REQUIRED");
            if (!StringUtils.hasText(entity.getSpecialities())) missing.add("PRIMARY_SPECIALITY_REQUIRED");
            if (entity.getYearsOfExperience() == null) missing.add("PRACTISING_SINCE_REQUIRED");
        } else if (entity.getProviderType() == ProviderType.CLINIC) {
            if (!StringUtils.hasText(entity.getOrganisationType())) missing.add("CLINIC_ORGANISATION_TYPE_REQUIRED");
            if (!StringUtils.hasText(entity.getFacilities())) missing.add("CLINIC_FACILITIES_REQUIRED");
        } else {
            if (!StringUtils.hasText(entity.getOwnership())) missing.add("HOSPITAL_OWNERSHIP_REQUIRED");
            if (!StringUtils.hasText(entity.getHospitalType())) missing.add("HOSPITAL_TYPE_REQUIRED");
            if (entity.getBeds() == null || entity.getBeds() <= 0) missing.add("HOSPITAL_BEDS_REQUIRED");
            if (!StringUtils.hasText(entity.getDepartments())) missing.add("HOSPITAL_DEPARTMENTS_REQUIRED");
            if (!StringUtils.hasText(entity.getMedicalDirector())) missing.add("HOSPITAL_MEDICAL_DIRECTOR_REQUIRED");
            if (!entity.isEmergencyAvailable()) missing.add("HOSPITAL_EMERGENCY_STATUS_REQUIRED");
        }
        return new ArrayList<>(missing);
    }

    private List<String> missingDocumentCodes(ProviderApplicationEntity entity, List<ProviderDocumentEntity> documentRecords) {
        Set<ProviderDocumentType> available = new LinkedHashSet<>();
        for (ProviderDocumentEntity record : documentRecords) {
            available.add(record.getDocumentType());
        }
        List<String> missing = new ArrayList<>();
        if (entity.getProviderType() == ProviderType.INDIVIDUAL_DOCTOR) {
            if (!available.contains(ProviderDocumentType.DOCTOR_PHOTO)) missing.add("DOCTOR_PHOTO_REQUIRED");
            if (!available.contains(ProviderDocumentType.REGISTRATION_CERTIFICATE)) missing.add("DOCTOR_REGISTRATION_CERTIFICATE_REQUIRED");
        } else if (entity.getProviderType() == ProviderType.CLINIC) {
            if (!available.contains(ProviderDocumentType.LOGO)) missing.add("CLINIC_LOGO_REQUIRED");
            if (!available.contains(ProviderDocumentType.REGISTRATION_CERTIFICATE)) missing.add("CLINIC_REGISTRATION_DOCUMENT_REQUIRED");
        } else {
            if (!available.contains(ProviderDocumentType.LOGO)) missing.add("HOSPITAL_LOGO_REQUIRED");
            if (!available.contains(ProviderDocumentType.REGISTRATION_CERTIFICATE)) missing.add("HOSPITAL_REGISTRATION_DOCUMENT_REQUIRED");
        }
        return missing;
    }

    private ProviderDashboardRecord buildDashboard(ProviderApplicationEntity entity) {
        ProviderApplicationRecord application = toRecord(entity, null);
        ProviderCompletionRecord completion = calculateCompletion(
                entity,
                currentLocations(entity.getId()),
                currentServices(entity.getId()),
                documents.findByProviderIdOrderByUploadedAtDesc(entity.getId())
        );
        List<ProviderTimelineEventRecord> timeline = history.findByProviderIdOrderByCreatedAtAsc(entity.getId()).stream().map(this::toTimelineEvent).toList();
        List<ProviderChangeRequestRecord> requestRecords = changeRequests.findByProviderIdOrderByRequestedAtDesc(entity.getId()).stream().map(this::toChangeRequest).toList();
        String nextAction = completion.canSubmit()
                ? "Submit for verification"
                : completion.incompleteSteps().isEmpty() ? "Continue registration" : "Complete " + completion.recommendedNextStep();
        if (isReadOnly(entity.getStatus())) {
            nextAction = entity.getStatus() == ProviderLifecycleStatus.SUBMITTED ? "Await review" : "Read only while under review";
        }
        if (entity.getStatus() == ProviderLifecycleStatus.CHANGES_REQUESTED) {
            nextAction = "Address requested changes";
        }
        return new ProviderDashboardRecord(application, completion, timeline, requestRecords, isReadOnly(entity.getStatus()), nextAction);
    }

    private ProviderTimelineEventRecord toTimelineEvent(ProviderStatusHistoryEntity entity) {
        return new ProviderTimelineEventRecord(
                timelineLabel(entity.getToStatus()),
                entity.getReason(),
                timelineActor(entity.getToStatus()),
                entity.getCreatedAt()
        );
    }

    private ProviderChangeRequestRecord toChangeRequest(ProviderChangeRequestEntity entity) {
        return new ProviderChangeRequestRecord(
                entity.getId(),
                entity.getSubmissionVersionNumber(),
                split(entity.getRequestedSections()),
                entity.getReviewerMessage(),
                entity.getProviderResponseNote(),
                entity.getRequestedAt(),
                entity.getResolvedAt(),
                entity.isResolved()
        );
    }

    private String snapshotJson(ProviderApplicationEntity entity, List<ProviderLocationEntity> locationRecords, List<ProviderServiceEntity> serviceRecords, List<ProviderDocumentEntity> documentRecords) {
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("id", entity.getId().toString());
            snapshot.put("referenceNumber", entity.getReferenceNumber());
            snapshot.put("providerType", entity.getProviderType().name());
            snapshot.put("status", entity.getStatus().name());
            snapshot.put("version", entity.getRowVersion());
            snapshot.put("email", entity.getEmail());
            snapshot.put("phone", entity.getPhone());
            snapshot.put("contactVerified", entity.isContactVerified());
            snapshot.put("termsAccepted", entity.isTermsAccepted());
            snapshot.put("privacyAccepted", entity.isPrivacyAccepted());
            snapshot.put("displayName", entity.getDisplayName());
            snapshot.put("legalName", entity.getLegalName());
            snapshot.put("organisationType", entity.getOrganisationType());
            snapshot.put("registrationNumber", entity.getRegistrationNumber());
            snapshot.put("gstNumber", entity.getGstNumber());
            snapshot.put("website", entity.getWebsite());
            snapshot.put("gender", entity.getGender());
            snapshot.put("dateOfBirth", entity.getDateOfBirth() == null ? null : entity.getDateOfBirth().toString());
            snapshot.put("languages", split(entity.getLanguages()));
            snapshot.put("biography", entity.getBiography());
            snapshot.put("medicalCouncil", entity.getMedicalCouncil());
            snapshot.put("qualification", entity.getQualification());
            snapshot.put("yearsOfExperience", entity.getYearsOfExperience());
            snapshot.put("specialities", split(entity.getSpecialities()));
            snapshot.put("subSpecialities", split(entity.getSubSpecialities()));
            snapshot.put("consultationFee", entity.getConsultationFee());
            snapshot.put("onlineConsultation", entity.isOnlineConsultation());
            snapshot.put("appointmentDurationMinutes", entity.getAppointmentDurationMinutes());
            snapshot.put("ownership", entity.getOwnership());
            snapshot.put("hospitalType", entity.getHospitalType());
            snapshot.put("beds", entity.getBeds());
            snapshot.put("emergencyAvailable", entity.isEmergencyAvailable());
            snapshot.put("medicalDirector", entity.getMedicalDirector());
            snapshot.put("departments", split(entity.getDepartments()));
            snapshot.put("facilities", split(entity.getFacilities()));
            snapshot.put("accreditations", split(entity.getAccreditations()));
            snapshot.put("branding", brandingSnapshot(entity));
            snapshot.put("locations", locationRecords.stream().map(this::locationSnapshot).toList());
            snapshot.put("services", serviceRecords.stream().map(this::serviceSnapshot).toList());
            snapshot.put("documents", documentRecords.stream().map(this::documentSnapshot).toList());
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to build submission snapshot", ex);
        }
    }

    private ProviderApplicationRecord toRecord(ProviderApplicationEntity entity, String onboardingToken) {
        List<ProviderLocationEntity> locationRecords = currentLocations(entity.getId());
        List<ProviderServiceEntity> serviceRecords = currentServices(entity.getId());
        List<ProviderDocumentEntity> documentRecords = documents.findByProviderIdOrderByUploadedAtDesc(entity.getId());
        List<String> missing = missingItems(entity, locationRecords, serviceRecords, documentRecords);
        return new ProviderApplicationRecord(
                entity.getId(),
                entity.getReferenceNumber(),
                entity.getProviderType(),
                entity.getStatus(),
                entity.getRowVersion(),
                entity.getCompletionPercent(),
                entity.getCurrentStep(),
                entity.getEmail(),
                entity.getPhone(),
                entity.isContactVerified(),
                entity.isTermsAccepted(),
                entity.isPrivacyAccepted(),
                entity.getDisplayName(),
                entity.getLegalName(),
                entity.getOrganisationType(),
                entity.getRegistrationNumber(),
                entity.getGstNumber(),
                entity.getWebsite(),
                entity.getGender(),
                entity.getDateOfBirth(),
                split(entity.getLanguages()),
                entity.getBiography(),
                entity.getMedicalCouncil(),
                entity.getQualification(),
                entity.getYearsOfExperience(),
                split(entity.getSpecialities()),
                split(entity.getSubSpecialities()),
                entity.getConsultationFee(),
                entity.isOnlineConsultation(),
                entity.getAppointmentDurationMinutes(),
                entity.getOwnership(),
                entity.getHospitalType(),
                entity.getBeds(),
                entity.isEmergencyAvailable(),
                entity.getMedicalDirector(),
                split(entity.getDepartments()),
                split(entity.getFacilities()),
                split(entity.getAccreditations()),
                branding(entity),
                locationRecords.stream().map(this::toLocation).toList(),
                serviceRecords.stream().map(this::toService).toList(),
                documentRecords.stream().map(this::toDocument).toList(),
                history.findByProviderIdOrderByCreatedAtAsc(entity.getId()).stream().map(this::toHistory).toList(),
                missing,
                entity.getLastSavedAt(),
                entity.getSubmittedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                onboardingToken
        );
    }

    private List<String> missingItems(ProviderApplicationEntity entity, List<ProviderLocationEntity> locationRecords, List<ProviderServiceEntity> serviceRecords, List<ProviderDocumentEntity> documentRecords) {
        List<String> missing = new ArrayList<>(missingFieldCodes(entity, locationRecords, serviceRecords));
        missing.addAll(missingDocumentCodes(entity, documentRecords));
        return missing;
    }

    private ProviderLifecycleStatus deriveProviderStatusAfterUpdate(ProviderApplicationEntity entity, ProviderCompletionRecord completion) {
        if (entity.getStatus() == ProviderLifecycleStatus.CHANGES_REQUESTED) {
            return ProviderLifecycleStatus.CHANGES_REQUESTED;
        }
        if (!entity.isContactVerified()) {
            return ProviderLifecycleStatus.DRAFT;
        }
        if (completion.canSubmit()) {
            return ProviderLifecycleStatus.READY_FOR_REVIEW;
        }
        if (completion.completionPercentage() >= 60) {
            return ProviderLifecycleStatus.PROFILE_INCOMPLETE;
        }
        return ProviderLifecycleStatus.CONTACT_VERIFIED;
    }

    private void resolveOpenChangeRequests(UUID providerId, String providerResponseNote) {
        changeRequests.findFirstByProviderIdAndResolvedAtIsNullOrderByRequestedAtDesc(providerId).ifPresent(changeRequest -> {
            changeRequest.setProviderResponseNote(normalizeNullable(providerResponseNote));
            changeRequest.markResolved();
            changeRequests.save(changeRequest);
        });
    }

    private void ensureEditable(ProviderApplicationEntity entity) {
        require(!isReadOnly(entity.getStatus()), "provider application is read only in its current status");
    }

    private void ensureEditableForSubmit(ProviderApplicationEntity entity) {
        require(
                entity.getStatus() == ProviderLifecycleStatus.DRAFT
                        || entity.getStatus() == ProviderLifecycleStatus.CONTACT_VERIFIED
                        || entity.getStatus() == ProviderLifecycleStatus.PROFILE_INCOMPLETE
                        || entity.getStatus() == ProviderLifecycleStatus.READY_FOR_REVIEW
                        || entity.getStatus() == ProviderLifecycleStatus.CHANGES_REQUESTED
                        || entity.getStatus() == ProviderLifecycleStatus.SUBMITTED,
                "provider application cannot be submitted from its current status"
        );
    }

    private boolean isReadOnly(ProviderLifecycleStatus status) {
        return READ_ONLY_STATUSES.contains(status);
    }

    private void verifyVersion(Long providedVersion, ProviderApplicationEntity entity) {
        if (providedVersion != null) {
            require(providedVersion == entity.getRowVersion(), "provider application changed in another session");
        }
    }

    private void transition(ProviderApplicationEntity entity, ProviderLifecycleStatus next, String reason) {
        ProviderLifecycleStatus previous = entity.getStatus();
        entity.setStatus(next);
        history.save(new ProviderStatusHistoryEntity(entity.getId(), previous, next, reason));
    }

    private void attachBrandingReference(ProviderApplicationEntity entity, ProviderDocumentEntity document) {
        if (document.getDocumentType() == ProviderDocumentType.LOGO) entity.setLogoDocumentId(document.getId());
        if (document.getDocumentType() == ProviderDocumentType.COVER_IMAGE) entity.setCoverImageDocumentId(document.getId());
        if (document.getDocumentType() == ProviderDocumentType.DOCTOR_PHOTO) entity.setDoctorPhotoDocumentId(document.getId());
    }

    private ProviderLocationEntity toEntity(UUID providerId, LocationCommand command) {
        return new ProviderLocationEntity(command.id(), providerId, normalizeNullable(command.label()), normalize(command.address()), normalize(command.city()), normalize(command.state()), normalize(command.country()), normalize(command.pinCode()), normalizeNullable(command.workingHours()), Boolean.TRUE.equals(command.parkingAvailable()), Boolean.TRUE.equals(command.accessibilityAvailable()));
    }

    private ProviderServiceEntity toEntity(UUID providerId, ServiceCommand command) {
        return new ProviderServiceEntity(command.id(), providerId, command.serviceType(), normalize(command.label()), normalizeNullable(command.description()), command.enabled() == null || command.enabled());
    }

    private LocationRecord toLocation(ProviderLocationEntity entity) {
        return new LocationRecord(entity.getId(), entity.getLabel(), entity.getAddress(), entity.getCity(), entity.getState(), entity.getCountry(), entity.getPinCode(), entity.getWorkingHours(), entity.isParkingAvailable(), entity.isAccessibilityAvailable());
    }

    private ServiceRecord toService(ProviderServiceEntity entity) {
        return new ServiceRecord(entity.getId(), entity.getServiceType(), entity.getLabel(), entity.getDescription(), entity.isEnabled());
    }

    private DocumentRecord toDocument(ProviderDocumentEntity entity) {
        return new DocumentRecord(entity.getId(), entity.getDocumentType(), entity.getOriginalFilename(), entity.getContentType(), entity.getSizeBytes(), entity.getUploadedAt(), entity.getVirusScanStatus());
    }

    private Map<String, Object> brandingSnapshot(ProviderApplicationEntity entity) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("logoDocumentId", entity.getLogoDocumentId() == null ? null : entity.getLogoDocumentId().toString());
        snapshot.put("coverImageDocumentId", entity.getCoverImageDocumentId() == null ? null : entity.getCoverImageDocumentId().toString());
        snapshot.put("doctorPhotoDocumentId", entity.getDoctorPhotoDocumentId() == null ? null : entity.getDoctorPhotoDocumentId().toString());
        snapshot.put("primaryColor", entity.getPrimaryColor());
        snapshot.put("tagline", entity.getTagline());
        return snapshot;
    }

    private Map<String, Object> locationSnapshot(ProviderLocationEntity entity) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", entity.getId().toString());
        snapshot.put("label", entity.getLabel());
        snapshot.put("address", entity.getAddress());
        snapshot.put("city", entity.getCity());
        snapshot.put("state", entity.getState());
        snapshot.put("country", entity.getCountry());
        snapshot.put("pinCode", entity.getPinCode());
        snapshot.put("workingHours", entity.getWorkingHours());
        snapshot.put("parkingAvailable", entity.isParkingAvailable());
        snapshot.put("accessibilityAvailable", entity.isAccessibilityAvailable());
        return snapshot;
    }

    private Map<String, Object> serviceSnapshot(ProviderServiceEntity entity) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", entity.getId().toString());
        snapshot.put("serviceType", entity.getServiceType().name());
        snapshot.put("label", entity.getLabel());
        snapshot.put("description", entity.getDescription());
        snapshot.put("enabled", entity.isEnabled());
        return snapshot;
    }

    private Map<String, Object> documentSnapshot(ProviderDocumentEntity entity) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", entity.getId().toString());
        snapshot.put("documentType", entity.getDocumentType().name());
        snapshot.put("originalFilename", entity.getOriginalFilename());
        snapshot.put("contentType", entity.getContentType());
        snapshot.put("sizeBytes", entity.getSizeBytes());
        snapshot.put("uploadedAt", entity.getUploadedAt().toString());
        snapshot.put("virusScanStatus", entity.getVirusScanStatus());
        return snapshot;
    }

    private StatusHistoryRecord toHistory(ProviderStatusHistoryEntity entity) {
        return new StatusHistoryRecord(entity.getId(), entity.getFromStatus(), entity.getToStatus(), entity.getReason(), entity.getCreatedAt());
    }

    private BrandingRecord branding(ProviderApplicationEntity entity) {
        return new BrandingRecord(entity.getLogoDocumentId(), entity.getCoverImageDocumentId(), entity.getDoctorPhotoDocumentId(), entity.getPrimaryColor(), entity.getTagline());
    }

    private List<ProviderLocationEntity> currentLocations(UUID providerId) {
        return locations.findByProviderIdOrderByLabelAsc(providerId);
    }

    private List<ProviderServiceEntity> currentServices(UUID providerId) {
        return services.findByProviderIdOrderByLabelAsc(providerId);
    }

    private String locationSummary(List<ProviderLocationEntity> records) {
        return records.isEmpty() ? "Location details pending" : records.get(0).getCity() + ", " + records.get(0).getState();
    }

    private String subtitle(ProviderApplicationEntity entity) {
        if (entity.getProviderType() == ProviderType.INDIVIDUAL_DOCTOR) return firstText(entity.getQualification(), entity.getMedicalCouncil(), "Doctor profile");
        if (entity.getProviderType() == ProviderType.CLINIC) return firstText(entity.getOrganisationType(), "Clinic profile");
        return firstText(entity.getHospitalType(), "Hospital profile");
    }

    private String providerNameRequiredCode(ProviderType providerType) {
        return switch (providerType) {
            case INDIVIDUAL_DOCTOR -> "DOCTOR_NAME_REQUIRED";
            case CLINIC -> "CLINIC_NAME_REQUIRED";
            case HOSPITAL -> "HOSPITAL_NAME_REQUIRED";
        };
    }

    private String registrationRequiredCode(ProviderType providerType) {
        return switch (providerType) {
            case INDIVIDUAL_DOCTOR -> "DOCTOR_REGISTRATION_NUMBER_REQUIRED";
            case CLINIC -> "CLINIC_REGISTRATION_NUMBER_REQUIRED";
            case HOSPITAL -> "HOSPITAL_REGISTRATION_NUMBER_REQUIRED";
        };
    }

    private String timelineLabel(ProviderLifecycleStatus status) {
        return switch (status) {
            case DRAFT -> "Registration started";
            case CONTACT_VERIFIED -> "Contact verified";
            case PROFILE_INCOMPLETE -> "Profile updated";
            case READY_FOR_REVIEW -> "Ready for submission";
            case SUBMITTED -> "Submitted for verification";
            case UNDER_REVIEW -> "Under review";
            case CHANGES_REQUESTED -> "Changes requested";
            case APPROVED -> "Approved";
            case PUBLISHED -> "Published";
            case SUSPENDED -> "Suspended";
            case ARCHIVED -> "Archived";
        };
    }

    private String timelineActor(ProviderLifecycleStatus status) {
        return switch (status) {
            case DRAFT, CONTACT_VERIFIED, PROFILE_INCOMPLETE, READY_FOR_REVIEW, SUBMITTED -> "Provider";
            case UNDER_REVIEW, CHANGES_REQUESTED, APPROVED, PUBLISHED, SUSPENDED, ARCHIVED -> "Review Team";
        };
    }

    private String statusReason(ProviderLifecycleStatus status) {
        return switch (status) {
            case CONTACT_VERIFIED -> "Contact verified";
            case PROFILE_INCOMPLETE -> "Profile completion progressed";
            case READY_FOR_REVIEW -> "Ready for review";
            default -> "Status updated";
        };
    }

    private String firstText(String... values) {
        for (String value : values) if (StringUtils.hasText(value)) return value;
        return "";
    }

    private String join(List<String> values) {
        if (values == null) return null;
        return String.join(",", values.stream().filter(StringUtils::hasText).map(String::trim).toList());
    }

    private List<String> split(String value) {
        if (!StringUtils.hasText(value)) return List.of();
        return List.of(value.split(",")).stream().map(String::trim).filter(StringUtils::hasText).toList();
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) throw new IllegalArgumentException("required text is missing");
        return value.trim();
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void requireText(String value, String message) {
        require(StringUtils.hasText(value), message);
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private ProviderApplicationEntity requireById(UUID id) {
        return applications.findById(id).orElseThrow(() -> new IllegalArgumentException("provider application not found"));
    }

    private ProviderApplicationEntity requireByToken(String token) {
        requireText(token, "provider token is required");
        return applications.findByTokenHash(digest(token)).orElseThrow(() -> new IllegalArgumentException("provider application not found"));
    }

    private void requireOwns(ProviderApplicationEntity entity, String token) {
        requireText(token, "provider token is required");
        require(entity.getTokenHash().equals(digest(token)), "provider application is not accessible");
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String digest(String value) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private record StepState(String code, String label, boolean complete) {
    }

}
