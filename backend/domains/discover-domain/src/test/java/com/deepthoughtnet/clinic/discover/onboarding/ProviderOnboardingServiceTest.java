package com.deepthoughtnet.clinic.discover.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
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
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderContactVerificationEntity;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderContactVerificationRepository;
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
import com.deepthoughtnet.clinic.discover.publicprofile.ProviderPublicProfileProjectionRepairService;
import com.deepthoughtnet.clinic.discover.publicprofile.ProviderPublicProfileService;
import com.deepthoughtnet.clinic.discover.reference.DiscoverReferenceCategory;
import com.deepthoughtnet.clinic.discover.reference.DiscoverReferenceDataService;
import com.deepthoughtnet.clinic.discover.reference.DiscoverReferenceOptionRecord;
import com.deepthoughtnet.clinic.discover.reference.InvalidReferenceValueException;
import com.deepthoughtnet.clinic.discover.verification.DiscoverVerificationService;
import com.deepthoughtnet.clinic.discover.verification.db.DiscoverProviderAccountEntity;
import com.deepthoughtnet.clinic.discover.verification.VerificationChallengeResult;
import com.deepthoughtnet.clinic.discover.verification.VerificationChannel;
import com.deepthoughtnet.clinic.discover.verification.VerificationChallengeRequest;
import com.deepthoughtnet.clinic.discover.verification.VerificationPurpose;
import com.deepthoughtnet.clinic.discover.verification.DiscoverVerificationService.VerificationVerificationRequest;
import com.deepthoughtnet.clinic.discover.verification.VerificationVerificationResult;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ProviderOnboardingServiceTest {
    private final List<ProviderLocationEntity> locations = new ArrayList<>();
    private final List<ProviderServiceEntity> services = new ArrayList<>();
    private final List<ProviderDocumentEntity> documents = new ArrayList<>();
    private final List<ProviderStatusHistoryEntity> history = new ArrayList<>();
    private final List<ProviderSubmissionEntity> submissions = new ArrayList<>();
    private final AtomicReference<ProviderContactVerificationEntity> contactVerification = new AtomicReference<>();
    private ProviderApplicationRepository applicationRepository;
    private ProviderApplicationEntity application;
    private ProviderOnboardingService service;
    private DiscoverVerificationService verificationService;
    private ObjectStorageService storage;
    private DiscoverReferenceDataService referenceDataService;
    private ProviderPublicProfileProjectionRepairService projectionRepairService;
    private ProviderPublicProfileService publicProfileService;

    @BeforeEach
    void setUp() {
        applicationRepository = Mockito.mock(ProviderApplicationRepository.class);
        ProviderLocationRepository locationRepository = Mockito.mock(ProviderLocationRepository.class);
        ProviderServiceRepository serviceRepository = Mockito.mock(ProviderServiceRepository.class);
        ProviderDocumentRepository documentRepository = Mockito.mock(ProviderDocumentRepository.class);
        ProviderSubmissionRepository submissionRepository = Mockito.mock(ProviderSubmissionRepository.class);
        ProviderStatusHistoryRepository historyRepository = Mockito.mock(ProviderStatusHistoryRepository.class);
        ProviderChangeRequestRepository changeRequestRepository = Mockito.mock(ProviderChangeRequestRepository.class);
        ProviderContactVerificationRepository contactVerificationRepository = Mockito.mock(ProviderContactVerificationRepository.class);
        publicProfileService = Mockito.mock(ProviderPublicProfileService.class);
        projectionRepairService = Mockito.mock(ProviderPublicProfileProjectionRepairService.class);
        verificationService = Mockito.mock(DiscoverVerificationService.class);
        referenceDataService = Mockito.mock(DiscoverReferenceDataService.class);
        storage = Mockito.mock(ObjectStorageService.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        when(applicationRepository.save(any(ProviderApplicationEntity.class))).thenAnswer(invocation -> {
            application = invocation.getArgument(0);
            return application;
        });
        when(applicationRepository.saveAndFlush(any(ProviderApplicationEntity.class))).thenAnswer(invocation -> {
            ProviderApplicationEntity saved = invocation.getArgument(0);
            if (application != null) {
                advanceRowVersion(saved);
            }
            application = saved;
            return saved;
        });
        when(applicationRepository.findById(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return application != null && application.getId().equals(id) ? Optional.of(application) : Optional.empty();
        });
        when(applicationRepository.findByTokenHash(any())).thenAnswer(invocation -> {
            String hash = invocation.getArgument(0);
            return application != null && application.getTokenHash().equals(hash) ? Optional.of(application) : Optional.empty();
        });
        when(applicationRepository.findByReferenceNumber(any())).thenAnswer(invocation -> {
            String referenceNumber = invocation.getArgument(0);
            return application != null && application.getReferenceNumber().equals(referenceNumber) ? Optional.of(application) : Optional.empty();
        });
        when(applicationRepository.findByReferenceNumberAndProviderAccountId(any(), any())).thenAnswer(invocation -> {
            String referenceNumber = invocation.getArgument(0);
            UUID providerAccountId = invocation.getArgument(1);
            return application != null
                    && application.getReferenceNumber().equals(referenceNumber)
                    && providerAccountId != null
                    && providerAccountId.equals(application.getProviderAccountId())
                    ? Optional.of(application)
                    : Optional.empty();
        });
        when(applicationRepository.findByStatusIn(any())).thenAnswer(invocation -> {
            List<ProviderLifecycleStatus> statuses = invocation.getArgument(0);
            if (application == null || statuses == null || !statuses.contains(application.getStatus())) {
                return List.of();
            }
            return List.of(application);
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
        when(documentRepository.findById(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return documents.stream().filter(document -> document.getId().equals(id)).findFirst();
        });
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
        when(contactVerificationRepository.findByProviderId(any())).thenAnswer(invocation -> {
            ProviderContactVerificationEntity current = contactVerification.get();
            UUID providerId = invocation.getArgument(0);
            return current != null && current.getProviderId().equals(providerId) ? Optional.of(current) : Optional.empty();
        });
        when(contactVerificationRepository.save(any(ProviderContactVerificationEntity.class))).thenAnswer(invocation -> {
            ProviderContactVerificationEntity row = invocation.getArgument(0);
            contactVerification.set(row);
            return row;
        });
        when(verificationService.requestChallenge(any(VerificationChallengeRequest.class))).thenReturn(
                new VerificationChallengeResult(
                        UUID.randomUUID(),
                        VerificationChannel.EMAIL,
                        "d*****@jeevanam.test",
                        "Verification code sent",
                        "123456",
                        "LOCAL",
                        OffsetDateTime.now().plusMinutes(5),
                        OffsetDateTime.now().plusSeconds(30),
                        300,
                        30,
                        "LOCAL",
                        "delivery-reference"));
        when(verificationService.verifyChallenge(any(VerificationVerificationRequest.class))).thenAnswer(invocation -> {
            VerificationVerificationRequest request = invocation.getArgument(0);
            ProviderContactVerificationEntity current = contactVerification.get();
            if (current != null) {
                if (request.channel() == VerificationChannel.EMAIL) {
                    current.markEmailVerified();
                } else {
                    current.markPhoneVerified();
                }
            }
            if (application != null) {
                application.setContactVerified(true);
            }
            return new VerificationVerificationResult(true, "Verification successful", UUID.randomUUID(), true, true, request.normalizedRecipient(), request.purpose(), request.channel());
        });
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
        when(referenceDataService.isAvailableForSubmission(any())).thenReturn(true);
        when(referenceDataService.requireService(any(), any())).thenAnswer(invocation -> {
            ProviderType providerType = invocation.getArgument(0);
            ProviderServiceType serviceType = invocation.getArgument(1);
            if (providerType == null || serviceType == null) {
                throw new IllegalArgumentException("serviceType is required");
            }
            return serviceOption(serviceType, providerType);
        });

        service = new ProviderOnboardingService(applicationRepository, locationRepository, serviceRepository, documentRepository, submissionRepository, historyRepository, changeRequestRepository, contactVerificationRepository, storage, objectMapper, publicProfileService, projectionRepairService, verificationService, referenceDataService);
    }

    @Test
    void startOrResumeOwnedApplicationBootstrapsPhoneAuthenticatedAccountWithoutEmail() {
        UUID providerAccountId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        DiscoverProviderAccountEntity account = DiscoverProviderAccountEntity.create(null, "9876501402");
        account.markPhoneVerified();
        when(verificationService.findAccountById(providerAccountId)).thenReturn(Optional.of(account));
        when(applicationRepository.findByProviderAccountIdOrderByUpdatedAtDesc(providerAccountId)).thenReturn(List.of());

        var start = service.startOrResumeOwnedApplication(ProviderType.CLINIC, providerAccountId, false);

        assertThat(start.providerType()).isEqualTo(ProviderType.CLINIC);
        assertThat(start.onboardingToken()).isNotBlank();
        assertThat(application).isNotNull();
        assertThat(application.getProviderAccountId()).isEqualTo(providerAccountId);
        assertThat(application.getEmail()).isNull();
        assertThat(application.getPhone()).isEqualTo("9876501402");
        assertThat(contactVerification.get()).isNotNull();
        assertThat(contactVerification.get().getEmailNormalized()).isNull();
        assertThat(contactVerification.get().getPhoneNormalized()).isEqualTo("9876501402");
    }

    @Test
    void createsDraftWithOpaqueResumeTokenAndAuditHistory() {
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.INDIVIDUAL_DOCTOR, "Doctor@Example.COM", "9999999999", "password-123", true, true));

        assertThat(created.onboardingToken()).isNotBlank();
        assertThat(created.referenceNumber()).startsWith("JDR-");
        assertThat(created.providerType()).isEqualTo(ProviderType.INDIVIDUAL_DOCTOR);
        assertThat(created.status()).isEqualTo(ProviderLifecycleStatus.DRAFT);
        assertThat(created.email()).isEqualTo("doctor@example.com");
        assertThat(history).singleElement().satisfies(row -> assertThat(row.getToStatus()).isEqualTo(ProviderLifecycleStatus.DRAFT));
    }

    @Test
    void persistsEachRequestedProviderTypeWithoutFallback() {
        var clinic = service.create(new CreateProviderApplicationCommand(ProviderType.CLINIC, "clinic@example.com", "9999999999", "password-123", true, true));
        var hospital = service.create(new CreateProviderApplicationCommand(ProviderType.HOSPITAL, "hospital@example.com", "9999999999", "password-123", true, true));

        assertThat(clinic.providerType()).isEqualTo(ProviderType.CLINIC);
        assertThat(hospital.providerType()).isEqualTo(ProviderType.HOSPITAL);
        assertThat(clinic.referenceNumber()).startsWith("JCL-");
        assertThat(hospital.referenceNumber()).startsWith("JHS-");
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
        assertThat(preview.services()).containsExactly("Consultation");
        assertThat(preview.specialities()).containsExactly("General Medicine");
    }

    @Test
    void persistsLocationCoordinatesAndProjectsThemIntoSnapshots() {
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.CLINIC, "clinic@example.com", "9999999999", "password-123", true, true));

        service.update(created.id(), created.onboardingToken(), new UpdateProviderApplicationCommand(
                0L,
                null,
                null,
                true,
                true,
                true,
                "Sunrise Family Clinic",
                "Sunrise Family Clinic",
                "Private clinic",
                "CLIN-100",
                null,
                "https://example.com",
                null,
                null,
                List.of("English"),
                "Primary care clinic.",
                null,
                null,
                null,
                List.of("General Medicine"),
                List.of(),
                null,
                true,
                15,
                "Private",
                null,
                null,
                false,
                null,
                List.of(),
                List.of("Parking"),
                List.of(),
                List.of(new LocationCommand(null, "Primary", "Baner Road", "Pune", "Maharashtra", "India", "411045", "Mon-Sat 9 AM-5 PM", true, true, new BigDecimal("18.520400"), new BigDecimal("73.856700"))),
                List.of(new ServiceCommand(null, ProviderServiceType.CONSULTATION, "Consultation", "OPD and follow-up consultations", true)),
                null
        ));

        var loaded = service.get(created.id(), created.onboardingToken());
        var preview = service.preview(created.id(), created.onboardingToken());

        assertThat(loaded.locations()).singleElement().satisfies(location -> {
            assertThat(location.latitude()).isEqualByComparingTo(new BigDecimal("18.520400"));
            assertThat(location.longitude()).isEqualByComparingTo(new BigDecimal("73.856700"));
        });
        assertThat(preview.locationSummary()).isEqualTo("Pune, Maharashtra");
    }

    @Test
    void preservesNullLocationCoordinatesForHistoricalLocations() {
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.CLINIC, "clinic-null@example.com", "9999999999", "password-123", true, true));

        service.update(created.id(), created.onboardingToken(), new UpdateProviderApplicationCommand(
                0L,
                null,
                null,
                true,
                true,
                true,
                "Jeevanam Family Clinic",
                "Jeevanam Family Clinic",
                "Private clinic",
                "CLIN-101",
                null,
                "https://example.com",
                null,
                null,
                List.of("English"),
                "Primary care clinic.",
                null,
                null,
                null,
                List.of("General Medicine"),
                List.of(),
                null,
                true,
                15,
                "Private",
                null,
                null,
                false,
                null,
                List.of(),
                List.of("Parking"),
                List.of(),
                List.of(new LocationCommand(null, "Primary", "Jeevanam Family Clinic", "Kharadi", "Maharashtra", "India", "411014", "Mon-Sat 9 AM-5 PM", true, true, null, null)),
                List.of(new ServiceCommand(null, ProviderServiceType.CONSULTATION, "Consultation", "OPD and follow-up consultations", true)),
                null
        ));

        var loaded = service.get(created.id(), created.onboardingToken());

        assertThat(loaded.locations()).singleElement().satisfies(location -> {
            assertThat(location.latitude()).isNull();
            assertThat(location.longitude()).isNull();
        });
    }

    @Test
    void listReviewApplicationsTreatsBlankSearchAsOptional() {
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.CLINIC, "review@example.com", "9999999999", "password-123", true, true));
        application.setStatus(ProviderLifecycleStatus.SUBMITTED);

        var rows = service.listReviewApplications(List.of(ProviderLifecycleStatus.SUBMITTED), null, null);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.referenceNumber()).isEqualTo(created.referenceNumber());
            assertThat(row.status()).isEqualTo(ProviderLifecycleStatus.SUBMITTED);
        });
    }

    @Test
    void updateReturnsFreshVersionAndRejectsStaleOptimisticLocksWithConflict() {
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.CLINIC, "clinic@example.com", "9999999999", "password-123", true, true));

        var updated = service.update(created.id(), created.onboardingToken(), new UpdateProviderApplicationCommand(
                0L,
                null,
                null,
                true,
                true,
                true,
                "Sunrise Family Clinic",
                "Sunrise Family Clinic",
                "Private clinic",
                "CLIN-100",
                null,
                "https://example.com",
                null,
                null,
                List.of("English"),
                "Primary care clinic.",
                null,
                null,
                null,
                List.of("General Medicine"),
                List.of(),
                null,
                true,
                15,
                "Private",
                null,
                null,
                false,
                null,
                List.of(),
                List.of("Parking"),
                List.of(),
                List.of(new LocationCommand(null, "Primary", "Baner Road", "Pune", "Maharashtra", "India", "411045", "Mon-Sat 9 AM-5 PM", true, true, null, null)),
                List.of(new ServiceCommand(null, ProviderServiceType.CONSULTATION, "Consultation", "OPD and follow-up consultations", true)),
                null
        ));

        assertThat(updated.version()).isEqualTo(1L);
        assertThatThrownBy(() -> service.update(created.id(), created.onboardingToken(), new UpdateProviderApplicationCommand(
                0L,
                null,
                null,
                true,
                true,
                true,
                "Sunrise Family Clinic",
                "Sunrise Family Clinic",
                "Private clinic",
                "CLIN-100",
                null,
                "https://example.com",
                null,
                null,
                List.of("English"),
                "Primary care clinic.",
                null,
                null,
                null,
                List.of("General Medicine"),
                List.of(),
                null,
                true,
                15,
                "Private",
                null,
                null,
                false,
                null,
                List.of(),
                List.of("Parking"),
                List.of(),
                List.of(new LocationCommand(null, "Primary", "Baner Road", "Pune", "Maharashtra", "India", "411045", "Mon-Sat 9 AM-5 PM", true, true, null, null)),
                List.of(new ServiceCommand(null, ProviderServiceType.CONSULTATION, "Consultation", "OPD and follow-up consultations", true)),
                null
        )))
                .isInstanceOf(ProviderOnboardingConflictException.class)
                .hasMessage("provider application changed in another session");
    }

    @Test
    void savesMultipleCanonicalServicesWithReferenceResolvedLabels() {
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.INDIVIDUAL_DOCTOR, "doctor-services@example.com", "9999999999", "password-123", true, true));

        service.update(created.id(), created.onboardingToken(), new UpdateProviderApplicationCommand(
                0L,
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
                List.of(new LocationCommand(null, "Primary", "Baner Road", "Pune", "Maharashtra", "India", "411045", "Mon-Sat 9 AM-5 PM", true, true, null, null)),
                List.of(
                        new ServiceCommand(null, ProviderServiceType.CONSULTATION, "Consultation", "OPD and follow-up consultations", true),
                        new ServiceCommand(null, ProviderServiceType.TELECONSULTATION, "Teleconsultation", "Virtual follow-up consultations", true),
                        new ServiceCommand(null, ProviderServiceType.HEALTH_CHECKUPS, "Health Checkups", "Preventive and annual health checks", true)
                ),
                null
        ));

        assertThat(services).hasSize(3);
        assertThat(services).extracting(ProviderServiceEntity::getServiceType)
                .containsExactlyInAnyOrder(
                        ProviderServiceType.CONSULTATION,
                        ProviderServiceType.TELECONSULTATION,
                        ProviderServiceType.HEALTH_CHECKUPS
                );
        assertThat(services).extracting(ProviderServiceEntity::getLabel)
                .containsExactlyInAnyOrder("Consultation", "Teleconsultation", "Health Checkups");
    }

    @Test
    void rejectsInactiveReferenceServicesBeforePersistence() {
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.CLINIC, "clinic-invalid-service@example.com", "9999999999", "password-123", true, true));
        doThrow(new InvalidReferenceValueException("services", "Selected service is not available for this provider type."))
                .when(referenceDataService)
                .requireService(eq(ProviderType.CLINIC), eq(ProviderServiceType.CONSULTATION));

        assertThatThrownBy(() -> service.update(created.id(), created.onboardingToken(), new UpdateProviderApplicationCommand(
                0L,
                null,
                null,
                true,
                true,
                true,
                "Sunrise Family Clinic",
                "Sunrise Family Clinic",
                "Private clinic",
                "CLIN-100",
                null,
                "https://example.com",
                null,
                null,
                List.of("English"),
                "Primary care clinic.",
                null,
                null,
                null,
                List.of("General Medicine"),
                List.of(),
                null,
                true,
                15,
                "Private",
                null,
                null,
                false,
                null,
                List.of(),
                List.of("Parking"),
                List.of(),
                List.of(new LocationCommand(null, "Primary", "Baner Road", "Pune", "Maharashtra", "India", "411045", "Mon-Sat 9 AM-5 PM", true, true, null, null)),
                List.of(new ServiceCommand(null, ProviderServiceType.CONSULTATION, "Consultation", "OPD and follow-up consultations", true)),
                null
        )))
                .isInstanceOf(InvalidReferenceValueException.class)
                .hasMessage("Selected service is not available for this provider type.");
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
    void reviewerDocumentContentReturnsPrivateVerificationDocumentBytesForOwnedApplication() {
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.CLINIC, "clinic@example.com", "9999999999", "password-123", true, true));
        var uploaded = service.uploadDocument(created.id(), created.onboardingToken(), new UploadedDocumentCommand(ProviderDocumentType.REGISTRATION_CERTIFICATE, "registration.pdf", "application/pdf", 4, new byte[] {1, 2, 3, 4}));
        when(storage.getObjectBytes("tenants/discover/onboarding/logo.png")).thenReturn(new byte[] {1, 2, 3, 4});

        var content = service.reviewDocumentContent(created.id(), uploaded.id());

        assertThat(content.documentId()).isEqualTo(uploaded.id());
        assertThat(content.contentType()).isEqualTo("application/pdf");
        assertThat(content.originalFilename()).isEqualTo("registration.pdf");
        assertThat(content.bytes()).containsExactly(1, 2, 3, 4);
    }

    @Test
    void reviewerDocumentContentBlocksSecurityScannedInfectedFiles() throws Exception {
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.CLINIC, "clinic@example.com", "9999999999", "password-123", true, true));
        var uploaded = service.uploadDocument(created.id(), created.onboardingToken(), new UploadedDocumentCommand(ProviderDocumentType.REGISTRATION_CERTIFICATE, "registration.pdf", "application/pdf", 4, new byte[] {1, 2, 3, 4}));
        var infected = documents.stream().filter(document -> document.getId().equals(uploaded.id())).findFirst().orElseThrow();
        var virusScanStatus = ProviderDocumentEntity.class.getDeclaredField("virusScanStatus");
        virusScanStatus.setAccessible(true);
        virusScanStatus.set(infected, "INFECTED");

        assertThatThrownBy(() -> service.reviewDocumentContent(created.id(), uploaded.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("This document was blocked by the security scan.");
    }

    @Test
    void submissionFailsWhenReferenceDataUnavailable() {
        when(referenceDataService.isAvailableForSubmission(any())).thenReturn(false);
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.CLINIC, "clinic@example.com", "9999999999", "password-123", true, true));

        assertThatThrownBy(() -> service.submit(created.id(), created.onboardingToken()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("REFERENCE_DATA_UNAVAILABLE");
    }

    @Test
    void submissionRequiresCompletedMandatoryProfileAndDocuments() {
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.HOSPITAL, "hospital@example.com", "9999999999", "password-123", true, true));

        assertThatThrownBy(() -> service.submit(created.id(), created.onboardingToken()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot submit until required fields are complete");
    }

    @Test
    void contactVerificationFlowIssuesChallengeAndSatisfiesSubmissionGate() {
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.CLINIC, "clinic@example.com", "9999999999", "password-123", true, true));

        var challenge = service.requestEmailVerification(created.id(), created.onboardingToken());
        assertThat(challenge.devCode()).isNotBlank();
        assertThat(challenge.message()).contains("Verification code");

        var verified = service.verifyEmail(created.id(), created.onboardingToken(), challenge.devCode());
        assertThat(verified.requirementSatisfied()).isTrue();
        assertThat(verified.emailStatus()).isEqualTo("VERIFIED");

        var loaded = service.get(created.id(), created.onboardingToken());
        assertThat(loaded.contactVerified()).isTrue();
        assertThat(loaded.contactVerification().requirementSatisfied()).isTrue();
    }

    @Test
    void completedApplicationSubmitsAndCreatesSubmissionHistory() {
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.INDIVIDUAL_DOCTOR, "doctor@example.com", "9999999999", "password-123", true, true));
        service.update(created.id(), created.onboardingToken(), completeDoctorCommand(0L));
        verifyEmailContact(created.id(), created.onboardingToken());
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
        verifyEmailContact(created.id(), created.onboardingToken());
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
    void publishTriggersHistoricalProjectionRepairAfterPublishingApprovedApplication() {
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.HOSPITAL, "hospital@example.com", "9999999999", "password-123", true, true));
        addSubmission(created.id(), 1, hospitalSnapshotJson(), ProviderLifecycleStatus.PUBLISHED);
        application.setStatus(ProviderLifecycleStatus.APPROVED);
        when(publicProfileService.publishApprovedApplication(any(), any(), any())).thenReturn(null);

        service.publish(created.id(), "Published after approval");

        verify(projectionRepairService).repairProviderApplication(created.id());
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
    void reviewDetailLoadsForClinicSnapshotWithoutDoctorNumericFields() {
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.CLINIC, "clinic-review@example.com", "9999999999", "password-123", true, true));
        addSubmission(created.id(), 1, clinicSnapshotJson(), ProviderLifecycleStatus.PUBLISHED);
        application.setStatus(ProviderLifecycleStatus.PUBLISHED);

        var detail = service.getApplicationForReview(created.referenceNumber());
        var dashboard = service.dashboard(created.onboardingToken());

        assertThat(detail.application().providerType()).isEqualTo(ProviderType.CLINIC);
        assertThat(detail.preview()).isNotNull();
        assertThat(dashboard.submittedSnapshot()).isNotNull();
        assertThat(dashboard.submittedSnapshot().providerType()).isEqualTo(ProviderType.CLINIC);
        assertThat(dashboard.submittedSnapshot().yearsOfExperience()).isNull();
        assertThat(dashboard.submittedSnapshot().beds()).isNull();
        assertThat(dashboard.submittedSnapshot().documentCount()).isEqualTo(1);
        assertThat(dashboard.submittedSnapshot().serviceCount()).isEqualTo(1);
        assertThat(dashboard.submittedSnapshot().locationCount()).isEqualTo(1);
    }

    @Test
    void reviewDetailLoadsForHospitalSnapshotWithBedsAndDepartments() {
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.HOSPITAL, "hospital-review@example.com", "9999999999", "password-123", true, true));
        addSubmission(created.id(), 1, hospitalSnapshotJson(), ProviderLifecycleStatus.PUBLISHED);
        application.setStatus(ProviderLifecycleStatus.PUBLISHED);

        var detail = service.getApplicationForReview(created.referenceNumber());
        var snapshot = detail.application() != null ? service.dashboard(created.onboardingToken()).submittedSnapshot() : null;

        assertThat(detail.application().providerType()).isEqualTo(ProviderType.HOSPITAL);
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.beds()).isEqualTo(250);
        assertThat(snapshot.departments()).containsExactly("General Medicine", "Family Medicine", "Dermatology");
        assertThat(snapshot.yearsOfExperience()).isNull();
        assertThat(snapshot.documentCount()).isEqualTo(2);
    }

    @Test
    void reviewDetailLoadsForDoctorSnapshotWithYearsOfExperience() {
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.INDIVIDUAL_DOCTOR, "doctor-review@example.com", "9999999999", "password-123", true, true));
        addSubmission(created.id(), 1, doctorSnapshotJson(), ProviderLifecycleStatus.PUBLISHED);
        application.setStatus(ProviderLifecycleStatus.PUBLISHED);

        var detail = service.getApplicationForReview(created.referenceNumber());
        var snapshot = service.dashboard(created.onboardingToken()).submittedSnapshot();

        assertThat(detail.application().providerType()).isEqualTo(ProviderType.INDIVIDUAL_DOCTOR);
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.yearsOfExperience()).isEqualTo(12);
        assertThat(snapshot.beds()).isNull();
        assertThat(snapshot.documentCount()).isEqualTo(2);
        assertThat(snapshot.galleryCount()).isEqualTo(1);
    }

    @Test
    void olderClinicSnapshotMissingOptionalNumericFieldsStillLoads() {
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.CLINIC, "clinic-old@example.com", "9999999999", "password-123", true, true));
        addSubmission(created.id(), 1, """
                {
                  "providerType": "CLINIC",
                  "displayName": "Old Clinic",
                  "legalName": "Old Clinic",
                  "specialities": ["General Medicine"],
                  "languages": ["English"],
                  "ownership": "Private",
                  "organisationType": "Standalone clinic",
                  "departments": [],
                  "facilities": ["Parking"]
                }
                """, ProviderLifecycleStatus.PUBLISHED);
        application.setStatus(ProviderLifecycleStatus.PUBLISHED);

        var snapshot = service.dashboard(created.onboardingToken()).submittedSnapshot();

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.yearsOfExperience()).isNull();
        assertThat(snapshot.beds()).isNull();
        assertThat(snapshot.consultationFee()).isNull();
        assertThat(snapshot.documentCount()).isZero();
        assertThat(snapshot.serviceCount()).isZero();
        assertThat(snapshot.locationCount()).isZero();
    }

    @Test
    void nullableIntegerFieldsDoNotThrowForSubmittedSnapshots() {
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.CLINIC, "clinic-null-int@example.com", "9999999999", "password-123", true, true));
        addSubmission(created.id(), 1, """
                {
                  "providerType": "CLINIC",
                  "displayName": "Null Clinic",
                  "legalName": "Null Clinic",
                  "specialities": ["General Medicine"],
                  "languages": ["English"],
                  "ownership": "Private",
                  "organisationType": "Standalone clinic",
                  "departments": [],
                  "facilities": ["Parking"],
                  "services": [{"label": "Consultation"}],
                  "locations": [{"label": "Primary"}],
                  "documents": [{"documentType": "LOGO"}]
                }
                """, ProviderLifecycleStatus.PUBLISHED);
        application.setStatus(ProviderLifecycleStatus.PUBLISHED);

        var detail = service.getApplicationForReview(created.referenceNumber());

        assertThat(detail.application().referenceNumber()).isEqualTo(created.referenceNumber());
        assertThat(detail.application().providerType()).isEqualTo(ProviderType.CLINIC);
    }

    @Test
    void contactVerifiedApplicationsRemainEditable() {
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.CLINIC, "clinic@example.com", "9999999999", "password-123", true, true));

        verifyEmailContact(created.id(), created.onboardingToken());
        service.update(created.id(), created.onboardingToken(), contactVerifiedDraftCommand(0L));

        var loaded = service.get(created.id(), created.onboardingToken());
        var completion = service.completion(created.id(), created.onboardingToken());

        assertThat(loaded.status()).isEqualTo(ProviderLifecycleStatus.CONTACT_VERIFIED);
        assertThat(loaded.submittedAt()).isNull();
        assertThat(completion.readOnly()).isFalse();
        assertThat(completion.canSubmit()).isFalse();
    }

    @Test
    void ownedDashboardLoadsExactReferenceWithoutFallback() {
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.INDIVIDUAL_DOCTOR, "doctor@example.com", "9999999999", "password-123", true, true));
        UUID providerAccountId = UUID.randomUUID();
        application.setProviderAccountId(providerAccountId);

        var dashboard = service.dashboardForOwnedApplication(created.referenceNumber(), providerAccountId);

        assertThat(dashboard.application().referenceNumber()).isEqualTo(created.referenceNumber());
        assertThat(dashboard.application().email()).isEqualTo("doctor@example.com");
    }

    @Test
    void ownedDashboardRejectsRequestsForUnownedApplicationReference() {
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.INDIVIDUAL_DOCTOR, "doctor@example.com", "9999999999", "password-123", true, true));
        application.setProviderAccountId(UUID.randomUUID());

        assertThatThrownBy(() -> service.dashboardForOwnedApplication(created.referenceNumber(), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("provider application not found");
    }

    @Test
    void issuingOnboardingAccessRotatesTokenForExactOwnedApplication() {
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.INDIVIDUAL_DOCTOR, "doctor@example.com", "9999999999", "password-123", true, true));
        UUID providerAccountId = UUID.randomUUID();
        application.setProviderAccountId(providerAccountId);
        String originalToken = created.onboardingToken();

        var access = service.issueOnboardingAccess(created.referenceNumber(), providerAccountId);

        assertThat(access.applicationId()).isEqualTo(created.id());
        assertThat(access.onboardingToken()).isNotBlank();
        assertThat(access.onboardingToken()).isNotEqualTo(originalToken);
        assertThat(service.get(created.id(), access.onboardingToken()).referenceNumber()).isEqualTo(created.referenceNumber());
        assertThatThrownBy(() -> service.get(created.id(), originalToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("provider application is not accessible");
    }

    @Test
    void resubmissionCreatesNewSubmissionVersionAfterChangeRequests() {
        var created = service.create(new CreateProviderApplicationCommand(ProviderType.INDIVIDUAL_DOCTOR, "doctor@example.com", "9999999999", "password-123", true, true));
        var updated = service.update(created.id(), created.onboardingToken(), completeDoctorCommand(0L));
        verifyEmailContact(created.id(), created.onboardingToken());
        service.uploadDocument(created.id(), created.onboardingToken(), new UploadedDocumentCommand(ProviderDocumentType.DOCTOR_PHOTO, "photo.png", "image/png", 4, new byte[] {1, 2, 3, 4}));
        service.uploadDocument(created.id(), created.onboardingToken(), new UploadedDocumentCommand(ProviderDocumentType.REGISTRATION_CERTIFICATE, "registration.pdf", "application/pdf", 4, new byte[] {1, 2, 3, 4}));
        var refreshed = service.get(created.id(), created.onboardingToken());
        service.update(created.id(), created.onboardingToken(), completeDoctorCommand(refreshed.version()));
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
                List.of(new LocationCommand(null, "Primary", "Baner Road", "Pune", "Maharashtra", "India", "411045", "Mon-Sat 9 AM-5 PM", true, true, null, null)),
                List.of(new ServiceCommand(null, ProviderServiceType.CONSULTATION, "Consultation", "OPD and follow-up consultations", true)),
                null
        );
    }

    private UpdateProviderApplicationCommand contactVerifiedDraftCommand(long version) {
        return new UpdateProviderApplicationCommand(
                version,
                null,
                null,
                true,
                true,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static void advanceRowVersion(ProviderApplicationEntity entity) {
        try {
            var field = ProviderApplicationEntity.class.getDeclaredField("rowVersion");
            field.setAccessible(true);
            field.setLong(entity, entity.getRowVersion() + 1);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Could not advance provider application row version", ex);
        }
    }

    private void verifyEmailContact(UUID providerId, String token) {
        var challenge = service.requestEmailVerification(providerId, token);
        assertThat(challenge.devCode()).isNotBlank();
        var verified = service.verifyEmail(providerId, token, challenge.devCode());
        assertThat(verified.requirementSatisfied()).isTrue();
    }

    private ProviderSubmissionEntity addSubmission(UUID providerId, int versionNumber, String snapshotJson, ProviderLifecycleStatus statusAfter) {
        ProviderSubmissionEntity submission = new ProviderSubmissionEntity(
                providerId,
                versionNumber,
                ProviderLifecycleStatus.DRAFT.name(),
                statusAfter.name(),
                "PROVIDER",
                "snapshot-" + versionNumber + "-" + providerId,
                snapshotJson,
                null
        );
        submissions.add(0, submission);
        return submission;
    }

    private String doctorSnapshotJson() {
        return """
                {
                  "providerType": "INDIVIDUAL_DOCTOR",
                  "displayName": "Dr Example",
                  "legalName": "Dr Example",
                  "specialities": ["General Medicine"],
                  "subSpecialities": [],
                  "languages": ["English"],
                  "qualification": "MBBS, MD",
                  "medicalCouncil": "Maharashtra Medical Council",
                  "yearsOfExperience": 12,
                  "consultationFee": 800,
                  "ownership": null,
                  "departments": [],
                  "facilities": ["Parking"],
                  "services": [{"label": "Consultation"}],
                  "locations": [{"label": "Primary"}],
                  "documents": [{"documentType": "DOCTOR_PHOTO"}, {"documentType": "GALLERY_IMAGE"}],
                  "branding": {"tagline": "Trusted doctor"}
                }
                """;
    }

    private String clinicSnapshotJson() {
        return """
                {
                  "providerType": "CLINIC",
                  "displayName": "Clinic Example",
                  "legalName": "Clinic Example",
                  "specialities": ["General Medicine"],
                  "subSpecialities": [],
                  "languages": ["English", "Hindi"],
                  "organisationType": "Standalone clinic",
                  "ownership": "Private",
                  "consultationFee": 600,
                  "departments": [],
                  "facilities": ["Parking"],
                  "services": [{"label": "Consultation"}],
                  "locations": [{"label": "Primary"}],
                  "documents": [{"documentType": "LOGO"}],
                  "branding": {"tagline": "Trusted clinic"}
                }
                """;
    }

    private String hospitalSnapshotJson() {
        return """
                {
                  "providerType": "HOSPITAL",
                  "displayName": "Hospital Example",
                  "legalName": "Hospital Example",
                  "specialities": ["General Medicine"],
                  "subSpecialities": [],
                  "languages": ["English", "Hindi"],
                  "hospitalType": "Multispeciality Hospital",
                  "ownership": "Private",
                  "beds": 250,
                  "medicalDirector": "Dr Example",
                  "consultationFee": 900,
                  "departments": ["General Medicine", "Family Medicine", "Dermatology"],
                  "facilities": ["Parking"],
                  "services": [{"label": "Consultation"}, {"label": "Health Checkups"}],
                  "locations": [{"label": "Primary"}],
                  "documents": [{"documentType": "LOGO"}, {"documentType": "GALLERY_IMAGE"}],
                  "branding": {"tagline": "Trusted hospital"}
                }
                """;
    }

    private DiscoverReferenceOptionRecord serviceOption(ProviderServiceType serviceType, ProviderType providerType) {
        String displayName = switch (serviceType) {
            case CONSULTATION -> "Consultation";
            case TELECONSULTATION -> "Teleconsultation";
            case HEALTH_CHECKUPS -> "Health Checkups";
            case VACCINATION -> "Vaccination";
            case MINOR_PROCEDURES -> "Minor Procedures";
            case HOME_VISIT -> "Home Visit";
            case LAB_COLLECTION -> "Lab Collection";
            case CHRONIC_DISEASE_MANAGEMENT -> "Chronic Disease Management";
            case PREVENTIVE_CARE -> "Preventive Care";
        };
        return new DiscoverReferenceOptionRecord(
                UUID.randomUUID(),
                DiscoverReferenceCategory.SERVICE,
                serviceType.name(),
                displayName,
                List.of(providerType),
                1,
                true
        );
    }
}
