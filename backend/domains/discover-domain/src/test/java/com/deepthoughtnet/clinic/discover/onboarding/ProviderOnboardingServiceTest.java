package com.deepthoughtnet.clinic.discover.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderDocumentType;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderLifecycleStatus;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderServiceType;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.CreateProviderApplicationCommand;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.LocationCommand;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.ServiceCommand;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.UpdateProviderApplicationCommand;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingModels.UploadedDocumentCommand;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderApplicationEntity;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderApplicationRepository;
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
import com.deepthoughtnet.clinic.discover.publicprofile.ProviderPublicProfileService;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ProviderOnboardingServiceTest {
    private final List<ProviderLocationEntity> locations = new ArrayList<>();
    private final List<ProviderServiceEntity> services = new ArrayList<>();
    private final List<ProviderDocumentEntity> documents = new ArrayList<>();
    private final List<ProviderStatusHistoryEntity> history = new ArrayList<>();
    private final List<ProviderSubmissionEntity> submissions = new ArrayList<>();
    private ProviderApplicationEntity application;
    private ProviderOnboardingService service;
    private ObjectStorageService storage;

    @BeforeEach
    void setUp() {
        ProviderApplicationRepository applicationRepository = Mockito.mock(ProviderApplicationRepository.class);
        ProviderLocationRepository locationRepository = Mockito.mock(ProviderLocationRepository.class);
        ProviderServiceRepository serviceRepository = Mockito.mock(ProviderServiceRepository.class);
        ProviderDocumentRepository documentRepository = Mockito.mock(ProviderDocumentRepository.class);
        ProviderSubmissionRepository submissionRepository = Mockito.mock(ProviderSubmissionRepository.class);
        ProviderStatusHistoryRepository historyRepository = Mockito.mock(ProviderStatusHistoryRepository.class);
        ProviderChangeRequestRepository changeRequestRepository = Mockito.mock(ProviderChangeRequestRepository.class);
        ProviderPublicProfileService publicProfileService = Mockito.mock(ProviderPublicProfileService.class);
        storage = Mockito.mock(ObjectStorageService.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        when(applicationRepository.save(any(ProviderApplicationEntity.class))).thenAnswer(invocation -> {
            application = invocation.getArgument(0);
            return application;
        });
        when(applicationRepository.findById(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return application != null && application.getId().equals(id) ? Optional.of(application) : Optional.empty();
        });
        when(applicationRepository.findByTokenHash(any())).thenAnswer(invocation -> {
            String hash = invocation.getArgument(0);
            return application != null && application.getTokenHash().equals(hash) ? Optional.of(application) : Optional.empty();
        });
        when(locationRepository.findByProviderIdOrderByLabelAsc(any())).thenReturn(locations);
        when(locationRepository.saveAll(any())).thenAnswer(invocation -> {
            locations.clear();
            Iterable<ProviderLocationEntity> incoming = invocation.getArgument(0);
            incoming.forEach(locations::add);
            return locations;
        });
        when(serviceRepository.findByProviderIdOrderByLabelAsc(any())).thenReturn(services);
        when(serviceRepository.saveAll(any())).thenAnswer(invocation -> {
            services.clear();
            Iterable<ProviderServiceEntity> incoming = invocation.getArgument(0);
            incoming.forEach(services::add);
            return services;
        });
        when(documentRepository.findByProviderIdOrderByUploadedAtDesc(any())).thenReturn(documents);
        when(documentRepository.save(any(ProviderDocumentEntity.class))).thenAnswer(invocation -> {
            ProviderDocumentEntity document = invocation.getArgument(0);
            documents.add(0, document);
            return document;
        });
        when(historyRepository.findByProviderIdOrderByCreatedAtAsc(any())).thenReturn(history);
        when(historyRepository.save(any(ProviderStatusHistoryEntity.class))).thenAnswer(invocation -> {
            ProviderStatusHistoryEntity row = invocation.getArgument(0);
            history.add(row);
            return row;
        });
        when(changeRequestRepository.findByProviderIdOrderByRequestedAtDesc(any())).thenReturn(List.of());
        when(changeRequestRepository.findFirstByProviderIdAndResolvedAtIsNullOrderByRequestedAtDesc(any())).thenReturn(Optional.empty());
        when(changeRequestRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(submissionRepository.countByProviderId(any())).thenAnswer(invocation -> submissions.size());
        when(submissionRepository.findByProviderIdOrderByVersionNumberDesc(any())).thenReturn(submissions);
        when(submissionRepository.findFirstByProviderIdOrderByVersionNumberDesc(any())).thenAnswer(invocation -> submissions.isEmpty() ? Optional.empty() : Optional.of(submissions.get(0)));
        when(submissionRepository.findFirstByProviderIdAndSnapshotHashOrderByVersionNumberDesc(any(), any())).thenAnswer(invocation -> {
            String hash = invocation.getArgument(1);
            return submissions.stream().filter(row -> row.getSnapshotHash().equals(hash)).findFirst();
        });
        when(submissionRepository.save(any(ProviderSubmissionEntity.class))).thenAnswer(invocation -> {
            ProviderSubmissionEntity row = invocation.getArgument(0);
            submissions.add(row);
            return row;
        });
        when(storage.buildDocumentStorageKey(any(), any())).thenReturn("tenants/discover/onboarding/logo.png");

        service = new ProviderOnboardingService(applicationRepository, locationRepository, serviceRepository, documentRepository, submissionRepository, historyRepository, changeRequestRepository, storage, objectMapper, publicProfileService);
    }

    @Test
    void createsDraftWithOpaqueResumeTokenAndAuditHistory() {
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.INDIVIDUAL_DOCTOR, "Doctor@Example.COM", "9999999999", "password-123", true, true));

        assertThat(created.onboardingToken()).isNotBlank();
        assertThat(created.referenceNumber()).startsWith("JDN-");
        assertThat(created.status()).isEqualTo(ProviderLifecycleStatus.DRAFT);
        assertThat(created.email()).isEqualTo("Doctor@Example.COM");
        assertThat(history).singleElement().satisfies(row -> assertThat(row.getToStatus()).isEqualTo(ProviderLifecycleStatus.DRAFT));
    }

    @Test
    void rejectsAccessWithWrongResumeToken() {
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.CLINIC, "clinic@example.com", "9999999999", "password-123", true, true));

        assertThatThrownBy(() -> service.get(created.id(), "wrong-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("provider application is not accessible");
    }

    @Test
    void savesStructuredDraftDataAndReturnsPreviewModel() {
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.INDIVIDUAL_DOCTOR, "doctor@example.com", "9999999999", "password-123", true, true));

        service.update(created.id(), created.onboardingToken(), completeDoctorCommand(0L));
        var preview = service.preview(created.id(), created.onboardingToken());

        assertThat(preview.displayName()).isEqualTo("Dr Anjali Sharma");
        assertThat(preview.locationSummary()).isEqualTo("Pune, Maharashtra");
        assertThat(preview.services()).containsExactly("Consultations");
        assertThat(preview.specialities()).containsExactly("General Medicine");
    }

    @Test
    void uploadsDocumentThroughObjectStorageAndLinksBrandingReference() {
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.CLINIC, "clinic@example.com", "9999999999", "password-123", true, true));

        var document = service.uploadDocument(created.id(), created.onboardingToken(), new UploadedDocumentCommand(ProviderDocumentType.LOGO, "logo.png", "image/png", 3, new byte[] {1, 2, 3}));
        var loaded = service.get(created.id(), created.onboardingToken());

        assertThat(document.originalFilename()).isEqualTo("logo.png");
        assertThat(loaded.branding().logoDocumentId()).isEqualTo(document.id());
        verify(storage).putObject(eq("tenants/discover/onboarding/logo.png"), eq("image/png"), aryEq(new byte[] {1, 2, 3}));
    }

    @Test
    void submissionRequiresCompletedMandatoryProfileAndDocuments() {
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.HOSPITAL, "hospital@example.com", "9999999999", "password-123", true, true));

        assertThatThrownBy(() -> service.submit(created.id(), created.onboardingToken()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot submit until required fields are complete");
    }

    @Test
    void completedApplicationSubmitsAndCreatesSubmissionHistory() {
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.INDIVIDUAL_DOCTOR, "doctor@example.com", "9999999999", "password-123", true, true));
        service.update(created.id(), created.onboardingToken(), completeDoctorCommand(0L));
        service.uploadDocument(created.id(), created.onboardingToken(), new UploadedDocumentCommand(ProviderDocumentType.DOCTOR_PHOTO, "photo.png", "image/png", 4, new byte[] {1, 2, 3, 4}));
        service.uploadDocument(created.id(), created.onboardingToken(), new UploadedDocumentCommand(ProviderDocumentType.REGISTRATION_CERTIFICATE, "registration.pdf", "application/pdf", 4, new byte[] {1, 2, 3, 4}));

        var submitted = service.submit(created.id(), created.onboardingToken());

        assertThat(submitted.status()).isEqualTo(ProviderLifecycleStatus.SUBMITTED);
        assertThat(submissions).singleElement().satisfies(row -> assertThat(row.getVersionNumber()).isEqualTo(1));
        assertThat(history).anySatisfy(row -> {
            assertThat(row.getToStatus()).isEqualTo(ProviderLifecycleStatus.SUBMITTED);
            assertThat(row.getReason()).isEqualTo("Submitted for verification");
        });
    }

    @Test
    void reviewerTransitionsAreAuditedAndDoNotOverwriteSubmissionHistory() {
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.INDIVIDUAL_DOCTOR, "doctor@example.com", "9999999999", "password-123", true, true));
        service.update(created.id(), created.onboardingToken(), completeDoctorCommand(0L));
        service.uploadDocument(created.id(), created.onboardingToken(), new UploadedDocumentCommand(ProviderDocumentType.DOCTOR_PHOTO, "photo.png", "image/png", 4, new byte[] {1, 2, 3, 4}));
        service.uploadDocument(created.id(), created.onboardingToken(), new UploadedDocumentCommand(ProviderDocumentType.REGISTRATION_CERTIFICATE, "registration.pdf", "application/pdf", 4, new byte[] {1, 2, 3, 4}));
        service.submit(created.id(), created.onboardingToken());

        var underReview = service.startReview(created.id(), "Verifier assigned");
        var changes = service.requestChanges(created.id(), "Add clearer registration document", List.of("DOCUMENTS"));

        assertThat(underReview.status()).isEqualTo(ProviderLifecycleStatus.UNDER_REVIEW);
        assertThat(changes.status()).isEqualTo(ProviderLifecycleStatus.CHANGES_REQUESTED);
        assertThat(submissions).singleElement();
        assertThat(history).extracting(ProviderStatusHistoryEntity::getToStatus)
                .contains(ProviderLifecycleStatus.SUBMITTED, ProviderLifecycleStatus.UNDER_REVIEW, ProviderLifecycleStatus.CHANGES_REQUESTED);
    }

    @Test
    void approvalRequiresUnderReviewStatus() {
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.CLINIC, "clinic@example.com", "9999999999", "password-123", true, true));

        assertThatThrownBy(() -> service.approve(created.id(), "Approved"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("provider application must be under review before approval");
    }

    @Test
    void rejectsUnsupportedUploadsBeforeStorageWrite() {
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.CLINIC, "clinic@example.com", "9999999999", "password-123", true, true));

        assertThatThrownBy(() -> service.uploadDocument(created.id(), created.onboardingToken(), new UploadedDocumentCommand(ProviderDocumentType.LOGO, "logo.webp", "image/webp", 3, new byte[] {1, 2, 3})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only PNG, JPEG, and PDF uploads are supported");
    }

    @Test
    void calculatesCompletionAndDashboardForIncompleteDrafts() {
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.CLINIC, "clinic@example.com", "9999999999", "password-123", true, true));

        var completion = service.completion(created.id(), created.onboardingToken());
        var dashboard = service.dashboard(created.onboardingToken());

        assertThat(completion.canSubmit()).isFalse();
        assertThat(completion.missingRequiredFields()).contains("CONTACT_VERIFICATION_REQUIRED", "CLINIC_NAME_REQUIRED", "CLINIC_REGISTRATION_NUMBER_REQUIRED");
        assertThat(dashboard.nextRecommendedAction()).isEqualTo("Complete Account and contact");
        assertThat(dashboard.timeline()).isNotEmpty();
    }

    @Test
    void resubmissionCreatesNewSubmissionVersionAfterChangeRequests() {
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.INDIVIDUAL_DOCTOR, "doctor@example.com", "9999999999", "password-123", true, true));
        service.update(created.id(), created.onboardingToken(), completeDoctorCommand(0L));
        service.uploadDocument(created.id(), created.onboardingToken(), new UploadedDocumentCommand(ProviderDocumentType.DOCTOR_PHOTO, "photo.png", "image/png", 4, new byte[] {1, 2, 3, 4}));
        service.uploadDocument(created.id(), created.onboardingToken(), new UploadedDocumentCommand(ProviderDocumentType.REGISTRATION_CERTIFICATE, "registration.pdf", "application/pdf", 4, new byte[] {1, 2, 3, 4}));
        service.update(created.id(), created.onboardingToken(), completeDoctorCommand(0L));
        service.submit(created.id(), created.onboardingToken());
        service.requestChanges(created.id(), "Add clearer registration document", List.of("DOCUMENTS"));
        service.resubmit(created.id(), created.onboardingToken(), "Updated registration document");

        assertThat(submissions).hasSize(2);
        assertThat(submissions).last().satisfies(row -> {
            assertThat(row.getVersionNumber()).isEqualTo(2);
            assertThat(row.getStatusBefore()).isEqualTo(ProviderLifecycleStatus.CHANGES_REQUESTED.name());
            assertThat(row.getSnapshotHash()).isNotBlank();
            assertThat(row.getSnapshotJson()).contains("doctor@example.com");
        });
        assertThat(history).extracting(ProviderStatusHistoryEntity::getToStatus)
                .contains(ProviderLifecycleStatus.CHANGES_REQUESTED, ProviderLifecycleStatus.SUBMITTED);
    }

    private UpdateProviderApplicationCommand completeDoctorCommand(long version) {
        return new UpdateProviderApplicationCommand(
                version,
                null,
                null,
                true,
                true,
                true,
                "Dr Anjali Sharma",
                "Dr Anjali Sharma",
                null,
                "MMC-123",
                null,
                "https://example.com",
                "Female",
                null,
                List.of("English", "Hindi"),
                "Family physician focused on primary care.",
                "Maharashtra Medical Council",
                "MBBS",
                15,
                List.of("General Medicine"),
                List.of("Family Medicine"),
                new BigDecimal("500.00"),
                true,
                15,
                null,
                null,
                null,
                false,
                null,
                List.of(),
                List.of("Parking"),
                List.of(),
                List.of(new LocationCommand(null, "Primary", "Baner Road", "Pune", "Maharashtra", "India", "411045", "Mon-Sat 9 AM-5 PM", true, true)),
                List.of(new ServiceCommand(null, ProviderServiceType.CONSULTATIONS, "Consultations", "OPD and follow-up consultations", true)),
                null
        );
    }
}
