package com.deepthoughtnet.clinic.platform.providerintegration.service;

import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.AvailabilityState;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingCapability;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingTargetReference;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.LinkLifecycleStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.MatchConfidence;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.MatchMethod;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PlatformConnectionStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.ProviderSourceReference;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileType;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProviderReference;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.SourceSystem;
import com.deepthoughtnet.clinic.platform.providerintegration.ProviderIntegrationTestApplication;
import com.deepthoughtnet.clinic.platform.providerintegration.db.PublicClinicPlatformLinkEntity;
import com.deepthoughtnet.clinic.platform.providerintegration.db.PublicClinicPlatformLinkRepository;
import com.deepthoughtnet.clinic.platform.providerintegration.db.PublicDoctorPracticePlatformLinkEntity;
import com.deepthoughtnet.clinic.platform.providerintegration.db.PublicDoctorPracticePlatformLinkRepository;
import com.deepthoughtnet.clinic.platform.providerintegration.model.PublicClinicPlatformLinkUpsertRequest;
import com.deepthoughtnet.clinic.platform.providerintegration.model.PublicDoctorPracticePlatformLinkUpsertRequest;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@SpringBootTest(
        classes = ProviderIntegrationTestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:providerintegration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driverClassName=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.flyway.enabled=false"
        }
)
class ProviderLinkingServiceTest {

    @Autowired
    private ProviderLinkingService service;

    @Autowired
    private PublicClinicPlatformLinkRepository clinicRepository;

    @Autowired
    private PublicDoctorPracticePlatformLinkRepository doctorRepository;

    @MockBean
    private AuditEventPublisher auditEventPublisher;

    @AfterEach
    void cleanUp() {
        doctorRepository.deleteAll();
        clinicRepository.deleteAll();
    }

    @Test
    void clinicLinkUpsertCreatesOpaqueBookingReferenceAndResolvesIt() {
        PublicClinicPlatformLinkEntity entity = service.upsertClinicLink(clinicRequest(
                "tenant-clinic-1",
                "public-clinic-1",
                3,
                LinkLifecycleStatus.LINKED,
                PlatformConnectionStatus.CONNECTED,
                AvailabilityState.AVAILABLE_TODAY
        ));

        assertThat(entity.getBookingCapability()).isEqualTo(BookingCapability.ONLINE_BOOKING);
        assertThat(entity.getBookingReference()).isNotBlank();
        assertThat(entity.getBookingReference()).doesNotContain("tenant-clinic-1");
        assertThat(entity.getBookingReference()).doesNotContain("public-clinic-1");
        assertThat(service.resolveBookingTarget(new BookingTargetReference(entity.getBookingReference(), entity.getCapabilityVersion()))).isPresent();
        verify(auditEventPublisher).record(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doctorPracticeLinksCanBeIndependentAcrossTenants() {
        PublicDoctorPracticePlatformLinkEntity tenantOne = service.upsertDoctorPracticeLink(doctorRequest(
                "tenant-a",
                "clinic-a",
                "doctor-a",
                "practice-a",
                "tenant-doctor-user-a",
                "tenant-doctor-profile-a",
                4
        ));
        PublicDoctorPracticePlatformLinkEntity tenantTwo = service.upsertDoctorPracticeLink(doctorRequest(
                "tenant-b",
                "clinic-b",
                "doctor-a",
                "practice-b",
                "tenant-doctor-user-b",
                "tenant-doctor-profile-b",
                4
        ));

        assertThat(doctorRepository.count()).isEqualTo(2);
        assertThat(tenantOne.getBookingReference()).isNotEqualTo(tenantTwo.getBookingReference());
        assertThat(service.resolveBookingTarget(new BookingTargetReference(tenantOne.getBookingReference(), tenantOne.getCapabilityVersion()))).isPresent();
        assertThat(service.resolveBookingTarget(new BookingTargetReference(tenantTwo.getBookingReference(), tenantTwo.getCapabilityVersion()))).isPresent();
    }

    @Test
    void duplicateActiveClinicLinkInTheSameTenantIsRejectedByUniqueness() {
        clinicRepository.save(service.upsertClinicLink(clinicRequest(
                "tenant-clinic-dup",
                "public-clinic-dup",
                1,
                LinkLifecycleStatus.APPROVED,
                PlatformConnectionStatus.NOT_CONNECTED,
                AvailabilityState.UNKNOWN
        )));

        PublicClinicPlatformLinkEntity duplicate = new PublicClinicPlatformLinkEntity();
        duplicate.setId(UUID.randomUUID());
        duplicate.setProviderType(PublicProfileType.CLINIC);
        duplicate.setSourceSystem(SourceSystem.HEALTHCARE_CLINIC);
        duplicate.setSourceEntityReference("source-clinic-dup");
        duplicate.setSourceRevision(2);
        duplicate.setTenantReference("tenant-clinic-dup");
        duplicate.setPlatformClinicReference("platform-tenant-clinic-dup");
        duplicate.setPublicClinicReference("public-clinic-dup");
        duplicate.setLinkStatus(LinkLifecycleStatus.APPROVED);
        duplicate.setConnectionStatus(PlatformConnectionStatus.NOT_CONNECTED);
        duplicate.setMatchMethod(MatchMethod.MANUAL_REFERENCE);
        duplicate.setAvailabilityState(AvailabilityState.UNKNOWN);
        duplicate.setBookingCapability(BookingCapability.CALL_TO_BOOK);
        duplicate.setBookingReference(UUID.randomUUID().toString());
        duplicate.setCapabilityVersion(1);
        duplicate.setConnectionRevision(1);
        duplicate.setActive(true);
        duplicate.setCreatedAt(OffsetDateTime.now());
        duplicate.setUpdatedAt(OffsetDateTime.now());

        assertThatThrownBy(() -> clinicRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void noSlotAvailabilityDoesNotChangeBookingCapability() {
        PublicClinicPlatformLinkEntity entity = service.upsertClinicLink(clinicRequest(
                "tenant-clinic-2",
                "public-clinic-2",
                2,
                LinkLifecycleStatus.LINKED,
                PlatformConnectionStatus.CONNECTED,
                AvailabilityState.AVAILABLE_TODAY
        ));

        service.updateAvailabilityState(
                new BookingTargetReference(entity.getBookingReference(), entity.getCapabilityVersion()),
                AvailabilityState.NO_SLOTS_IN_RANGE,
                "actor-1",
                "no slots in current range"
        );

        BookingTargetReference reference = new BookingTargetReference(entity.getBookingReference(), entity.getCapabilityVersion());
        assertThat(service.resolveBookingCapability(reference)).isEqualTo(BookingCapability.ONLINE_BOOKING);
        assertThat(service.resolveAvailabilityState(reference)).isEqualTo(AvailabilityState.NO_SLOTS_IN_RANGE);
    }

    @Test
    void olderSourceRevisionDoesNotOverwriteNewerState() {
        PublicClinicPlatformLinkEntity latest = service.upsertClinicLink(clinicRequest(
                "tenant-clinic-3",
                "public-clinic-3",
                5,
                LinkLifecycleStatus.LINKED,
                PlatformConnectionStatus.CONNECTED,
                AvailabilityState.AVAILABLE_TODAY
        ));

        assertThatThrownBy(() -> service.upsertClinicLink(clinicRequest(
                "tenant-clinic-3",
                "public-clinic-3",
                4,
                LinkLifecycleStatus.APPROVED,
                PlatformConnectionStatus.NOT_CONNECTED,
                AvailabilityState.UNKNOWN
        ))).isInstanceOf(ProviderConnectionConflictException.class)
                .extracting(exception -> ((ProviderConnectionConflictException) exception).getCode())
                .isEqualTo("stale_match_revision");

        PublicClinicPlatformLinkEntity reloaded = clinicRepository.findById(latest.getId()).orElseThrow();
        assertThat(reloaded.getSourceRevision()).isEqualTo(5);
        assertThat(reloaded.getBookingCapability()).isEqualTo(BookingCapability.ONLINE_BOOKING);
    }

    @Test
    void publicOnlyOrNotConnectedProfilesResolveToCallToBook() {
        PublicClinicPlatformLinkEntity entity = service.upsertClinicLink(clinicRequest(
                "tenant-clinic-4",
                "public-clinic-4",
                1,
                LinkLifecycleStatus.APPROVED,
                PlatformConnectionStatus.NOT_CONNECTED,
                AvailabilityState.UNKNOWN
        ));

        assertThat(entity.getBookingCapability()).isEqualTo(BookingCapability.CALL_TO_BOOK);
    }

    @Test
    void proposalVerificationActivationAndRetryAreCanonicalAndIdempotent() {
        PublicClinicPlatformLinkEntity proposed = service.upsertClinicLink(clinicRequest(
                "tenant-lifecycle",
                "public-lifecycle",
                20,
                LinkLifecycleStatus.PROPOSED,
                PlatformConnectionStatus.CONNECTION_PENDING,
                AvailabilityState.UNKNOWN
        ));
        PublicClinicPlatformLinkEntity verified = service.upsertClinicLink(clinicRequest(
                "tenant-lifecycle",
                "public-lifecycle",
                20,
                LinkLifecycleStatus.APPROVED,
                PlatformConnectionStatus.NOT_CONNECTED,
                AvailabilityState.UNKNOWN
        ));
        PublicClinicPlatformLinkEntity active = service.upsertClinicLink(clinicRequest(
                "tenant-lifecycle",
                "public-lifecycle",
                20,
                LinkLifecycleStatus.LINKED,
                PlatformConnectionStatus.CONNECTED,
                AvailabilityState.UNKNOWN
        ));
        long revision = active.getConnectionRevision();
        PublicClinicPlatformLinkEntity retry = service.upsertClinicLink(clinicRequest(
                "tenant-lifecycle",
                "public-lifecycle",
                20,
                LinkLifecycleStatus.LINKED,
                PlatformConnectionStatus.CONNECTED,
                AvailabilityState.UNKNOWN
        ));

        assertThat(proposed.getId()).isEqualTo(verified.getId()).isEqualTo(active.getId());
        assertThat(active.isActive()).isTrue();
        assertThat(active.getProposedAt()).isNotNull();
        assertThat(active.getVerifiedAt()).isNotNull();
        assertThat(active.getActivatedAt()).isNotNull();
        assertThat(active.getBookingCapability()).isEqualTo(BookingCapability.ONLINE_BOOKING);
        assertThat(retry.getConnectionRevision()).isEqualTo(revision);
        assertThat(clinicRepository.count()).isEqualTo(1);
    }

    @Test
    void activeLinkUsesOperationalCallToBookInsteadOfInferringOnlineBooking() {
        PublicClinicPlatformLinkUpsertRequest base = clinicRequest(
                "tenant-call",
                "public-call",
                20,
                LinkLifecycleStatus.LINKED,
                PlatformConnectionStatus.CONNECTED,
                AvailabilityState.UNKNOWN
        );
        PublicClinicPlatformLinkEntity active = service.upsertClinicLink(new PublicClinicPlatformLinkUpsertRequest(
                base.sourceReference(), base.publicClinicReference(), base.tenantReference(), base.platformClinicReference(),
                base.linkStatus(), base.connectionStatus(), base.matchMethod(), base.matchConfidence(), base.availabilityState(),
                base.evidenceSnapshotJson(), base.actorType(), base.actorReference(), base.reason(), BookingCapability.CALL_TO_BOOK,
                "No public doctor availability is configured."
        ));

        assertThat(active.getBookingCapability()).isEqualTo(BookingCapability.CALL_TO_BOOK);
        assertThat(active.getCapabilityReason()).contains("No public doctor availability");
    }

    @Test
    void unlinkAndRelinkReuseTheSameLinkRowAndBookingReference() {
        PublicClinicPlatformLinkEntity linked = service.upsertClinicLink(clinicRequest(
                "tenant-clinic-5",
                "public-clinic-5",
                1,
                LinkLifecycleStatus.LINKED,
                PlatformConnectionStatus.CONNECTED,
                AvailabilityState.AVAILABLE_TODAY
        ));

        PublicClinicPlatformLinkEntity unlinked = service.upsertClinicLink(clinicRequest(
                "tenant-clinic-5",
                "public-clinic-5",
                2,
                LinkLifecycleStatus.UNLINKED,
                PlatformConnectionStatus.DISCONNECTED,
                AvailabilityState.TEMPORARILY_UNAVAILABLE
        ));

        PublicClinicPlatformLinkEntity relinked = service.upsertClinicLink(clinicRequest(
                "tenant-clinic-5",
                "public-clinic-5",
                3,
                LinkLifecycleStatus.PROPOSED,
                PlatformConnectionStatus.CONNECTION_PENDING,
                AvailabilityState.AVAILABLE_TODAY
        ));

        assertThat(unlinked.getId()).isEqualTo(linked.getId());
        assertThat(relinked.getId()).isEqualTo(linked.getId());
        assertThat(relinked.getBookingReference()).isEqualTo(linked.getBookingReference());
        assertThat(clinicRepository.count()).isEqualTo(1);
    }

    @Test
    void suspendAndResumePreserveTheConnectionIdentityAndAuditMetadata() {
        PublicClinicPlatformLinkEntity linked = service.upsertClinicLink(clinicRequest(
                "tenant-suspend", "public-suspend", 20,
                LinkLifecycleStatus.LINKED, PlatformConnectionStatus.CONNECTED, AvailabilityState.UNKNOWN));

        PublicClinicPlatformLinkEntity suspended = service.upsertClinicLink(clinicRequest(
                "tenant-suspend", "public-suspend", 20,
                LinkLifecycleStatus.SUSPENDED, PlatformConnectionStatus.DISCONNECTED, AvailabilityState.UNKNOWN));
        String bookingReference = suspended.getBookingReference();

        assertThat(suspended.isActive()).isFalse();
        assertThat(suspended.getSuspendedAt()).isNotNull();
        assertThat(suspended.getSuspendedBy()).isEqualTo("actor-tenant-suspend");
        assertThat(suspended.getUnlinkedAt()).isNull();

        PublicClinicPlatformLinkEntity resumed = service.upsertClinicLink(clinicRequest(
                "tenant-suspend", "public-suspend", 20,
                LinkLifecycleStatus.LINKED, PlatformConnectionStatus.CONNECTED, AvailabilityState.UNKNOWN));

        assertThat(resumed.getId()).isEqualTo(linked.getId());
        assertThat(resumed.getBookingReference()).isEqualTo(bookingReference);
        assertThat(resumed.isActive()).isTrue();
        assertThat(clinicRepository.count()).isEqualTo(1);
    }

    @Test
    void invalidLinkAndConnectionCombinationIsRejected() {
        assertThatThrownBy(() -> service.upsertClinicLink(clinicRequest(
                "tenant-clinic-invalid",
                "public-clinic-invalid",
                1,
                LinkLifecycleStatus.REJECTED,
                PlatformConnectionStatus.CONNECTED,
                AvailabilityState.UNKNOWN
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid link/connection combination");
    }

    private PublicClinicPlatformLinkUpsertRequest clinicRequest(
            String tenantReference,
            String publicClinicReference,
            long sourceRevision,
            LinkLifecycleStatus linkStatus,
            PlatformConnectionStatus connectionStatus,
            AvailabilityState availabilityState
    ) {
        return new PublicClinicPlatformLinkUpsertRequest(
                new ProviderSourceReference(SourceSystem.HEALTHCARE_CLINIC, "clinic-source-" + tenantReference, sourceRevision, OffsetDateTime.now()),
                publicClinicReference,
                tenantReference,
                "platform-" + tenantReference,
                linkStatus,
                connectionStatus,
                MatchMethod.PLATFORM_ADMIN_REVIEW,
                MatchConfidence.HIGH,
                availabilityState,
                "[]",
                "PLATFORM_ADMIN",
                "actor-" + tenantReference,
                "link request for " + tenantReference,
                BookingCapability.ONLINE_BOOKING,
                "active appointment configuration"
        );
    }

    private PublicDoctorPracticePlatformLinkUpsertRequest doctorRequest(
            String tenantReference,
            String platformClinicReference,
            String publicDoctorId,
            String publicPracticeId,
            String tenantDoctorUserReference,
            String tenantDoctorProfileReference,
            long sourceRevision
    ) {
        return new PublicDoctorPracticePlatformLinkUpsertRequest(
                new ProviderSourceReference(SourceSystem.HEALTHCARE_DOCTOR, "doctor-source-" + tenantReference, sourceRevision, OffsetDateTime.now()),
                new PublicProviderReference(publicDoctorId, null),
                new PublicProviderReference(null, publicPracticeId),
                tenantReference,
                platformClinicReference,
                tenantDoctorUserReference,
                tenantDoctorProfileReference,
                LinkLifecycleStatus.LINKED,
                PlatformConnectionStatus.CONNECTED,
                MatchMethod.REGISTRATION_EXACT,
                MatchConfidence.HIGH,
                AvailabilityState.AVAILABLE_TODAY,
                "[]",
                "PLATFORM_ADMIN",
                "actor-" + tenantReference,
                "doctor practice link for " + tenantReference,
                BookingCapability.ONLINE_BOOKING,
                "active appointment configuration"
        );
    }
}
