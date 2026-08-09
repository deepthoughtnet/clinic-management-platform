package com.deepthoughtnet.clinic.api.discover.publicdoctorpracticeassociation;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.config.PersistenceScanConfig;
import com.deepthoughtnet.clinic.api.support.AbstractPostgresDataJpaTest;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.publicdoctorpracticeassociation.PublicDoctorPracticeAssociationService;
import com.deepthoughtnet.clinic.discover.publicdoctorpracticeassociation.db.DiscoverPublicDoctorPracticeAssociationRepository;
import com.deepthoughtnet.clinic.discover.publicprofile.db.DiscoverPublicProviderProfileEntity;
import com.deepthoughtnet.clinic.discover.publicprofile.db.DiscoverPublicProviderProfileRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({
        PersistenceScanConfig.class,
        PublicDoctorPracticeAssociationService.class
})
class PublicDoctorPracticeAssociationServicePersistenceTest extends AbstractPostgresDataJpaTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-08T11:11:36.409Z");

    @Autowired
    private PublicDoctorPracticeAssociationService service;

    @Autowired
    private DiscoverPublicDoctorPracticeAssociationRepository associationRepository;

    @Autowired
    private DiscoverPublicProviderProfileRepository profileRepository;

    @Test
    void reconcileClinicDoctorsCreatesAssociationsAndUpdatesDoctorCount() {
        UUID clinicId = UUID.fromString("fb6977b3-683b-40a3-95b8-05ffbad1dac0");
        UUID doctorOneId = UUID.fromString("ff4d7d2a-401a-4993-9814-afe2863275b6");
        UUID doctorTwoId = UUID.randomUUID();
        profileRepository.save(clinicProfile(clinicId, "green-valley-family-clinic", "Green Valley Family Clinic"));
        profileRepository.save(doctorProfile(doctorOneId, "amit-verma-2", "Amit Verma"));
        profileRepository.save(doctorProfile(doctorTwoId, "dr-second", "Second Doctor"));

        int doctorCount = service.reconcileClinicDoctors(clinicId, clinicId, List.of(doctorOneId, doctorTwoId), NOW);

        assertThat(doctorCount).isEqualTo(2);
        assertThat(associationRepository.findByPublicPracticeReferenceOrderByCreatedAtAsc(clinicId)).hasSize(2);
        assertThat(profileRepository.findByProviderId(clinicId)).isPresent().get()
                .extracting(DiscoverPublicProviderProfileEntity::getDoctorCount)
                .isEqualTo(2);

        int repeatCount = service.reconcileClinicDoctors(clinicId, clinicId, List.of(doctorOneId, doctorTwoId), NOW.plusMinutes(5));

        assertThat(repeatCount).isEqualTo(2);
        assertThat(associationRepository.findByPublicPracticeReferenceOrderByCreatedAtAsc(clinicId)).hasSize(2);
    }

    @Test
    void unpublishedDoctorIsRemovedFromVisibleClinicDoctorsWithoutDeletingHistory() {
        UUID clinicId = UUID.fromString("fb6977b3-683b-40a3-95b8-05ffbad1dac0");
        UUID doctorId = UUID.fromString("ff4d7d2a-401a-4993-9814-afe2863275b6");
        profileRepository.save(clinicProfile(clinicId, "green-valley-family-clinic", "Green Valley Family Clinic"));
        DiscoverPublicProviderProfileEntity doctor = profileRepository.save(doctorProfile(doctorId, "amit-verma-2", "Amit Verma"));

        service.reconcileClinicDoctors(clinicId, clinicId, List.of(doctorId), NOW);
        assertThat(service.listPublishedDoctorReferencesByPractice(clinicId)).containsExactly(doctorId);

        doctor.markUnpublished(NOW.plusMinutes(2));
        profileRepository.save(doctor);
        int doctorCount = service.reconcileClinicDoctors(clinicId, clinicId, List.of(), NOW.plusMinutes(3));

        assertThat(doctorCount).isZero();
        assertThat(service.listPublishedDoctorReferencesByPractice(clinicId)).isEmpty();
        assertThat(associationRepository.findByPublicPracticeReferenceOrderByCreatedAtAsc(clinicId))
                .singleElement()
                .satisfies(association -> {
                    assertThat(association.isActive()).isFalse();
                    assertThat(association.getPublicDoctorReference()).isEqualTo(doctorId);
                });
        assertThat(profileRepository.findByProviderId(clinicId)).isPresent().get()
                .extracting(DiscoverPublicProviderProfileEntity::getDoctorCount)
                .isEqualTo(0);
    }

    @Test
    void sameNameDoctorsInDifferentPracticesRemainSeparate() {
        UUID clinicOneId = UUID.fromString("fb6977b3-683b-40a3-95b8-05ffbad1dac0");
        UUID clinicTwoId = UUID.fromString("4db5fc3b-9c5f-4355-b69b-618446159201");
        UUID doctorOneId = UUID.fromString("ff4d7d2a-401a-4993-9814-afe2863275b6");
        UUID doctorTwoId = UUID.fromString("23cf0f04-3152-46ef-a0f6-3b243f90bbc5");
        profileRepository.save(clinicProfile(clinicOneId, "green-valley-family-clinic", "Green Valley Family Clinic"));
        profileRepository.save(clinicProfile(clinicTwoId, "jeevanam-family-clinic-local", "Jeevanam Family Clinic Local"));
        profileRepository.save(doctorProfile(doctorOneId, "amit-verma-2", "Amit Verma"));
        profileRepository.save(doctorProfile(doctorTwoId, "amit-verma", "Amit Verma"));

        service.reconcileClinicDoctors(clinicOneId, clinicOneId, List.of(doctorOneId), NOW);
        service.reconcileClinicDoctors(clinicTwoId, clinicTwoId, List.of(doctorTwoId), NOW);

        assertThat(associationRepository.findByPublicPracticeReferenceOrderByCreatedAtAsc(clinicOneId))
                .extracting(association -> association.getPublicDoctorReference())
                .containsExactly(doctorOneId);
        assertThat(associationRepository.findByPublicPracticeReferenceOrderByCreatedAtAsc(clinicTwoId))
                .extracting(association -> association.getPublicDoctorReference())
                .containsExactly(doctorTwoId);
        assertThat(associationRepository.findAll()).hasSize(2);
    }

    @Test
    void sameDoctorCanBeAssociatedWithMultiplePracticesAndReactivatedWithoutDuplication() {
        UUID clinicOneId = UUID.fromString("fb6977b3-683b-40a3-95b8-05ffbad1dac0");
        UUID clinicTwoId = UUID.fromString("4db5fc3b-9c5f-4355-b69b-618446159201");
        UUID doctorId = UUID.fromString("ff4d7d2a-401a-4993-9814-afe2863275b6");
        profileRepository.save(clinicProfile(clinicOneId, "green-valley-family-clinic", "Green Valley Family Clinic"));
        profileRepository.save(clinicProfile(clinicTwoId, "jeevanam-family-clinic-local", "Jeevanam Family Clinic Local"));
        profileRepository.save(doctorProfile(doctorId, "amit-verma-2", "Amit Verma"));

        var first = service.upsertActiveAssociation(doctorId, clinicOneId, PublicDoctorPracticeAssociationService.SOURCE_SYSTEM_HEALTHCARE, doctorId, clinicOneId, NOW);
        var second = service.upsertActiveAssociation(doctorId, clinicTwoId, PublicDoctorPracticeAssociationService.SOURCE_SYSTEM_HEALTHCARE, doctorId, clinicTwoId, NOW);

        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(service.findActiveAssociationsByPublicDoctorReference(doctorId)).hasSize(2);
        assertThat(service.findActiveAssociationsByPublicPracticeReference(clinicOneId)).singleElement().satisfies(association -> assertThat(association.isActive()).isTrue());
        assertThat(service.findActiveAssociationsByPublicPracticeReference(clinicTwoId)).singleElement().satisfies(association -> assertThat(association.isActive()).isTrue());
        assertThat(service.listPublishedPracticeReferencesByDoctor(doctorId)).containsExactly(clinicOneId, clinicTwoId);

        service.deactivateAssociation(doctorId, clinicOneId, PublicDoctorPracticeAssociationService.SOURCE_SYSTEM_HEALTHCARE, doctorId, clinicOneId, NOW.plusMinutes(10));
        assertThat(service.findActiveAssociationsByPublicPracticeReference(clinicOneId)).isEmpty();
        assertThat(associationRepository.findByPublicPracticeReferenceOrderByCreatedAtAsc(clinicOneId))
                .singleElement()
                .satisfies(association -> assertThat(association.isActive()).isFalse());
        assertThat(service.listPublishedPracticeReferencesByDoctor(doctorId)).containsExactly(clinicTwoId);

        service.upsertActiveAssociation(doctorId, clinicOneId, PublicDoctorPracticeAssociationService.SOURCE_SYSTEM_HEALTHCARE, doctorId, clinicOneId, NOW.plusMinutes(20));
        assertThat(associationRepository.findByPublicPracticeReferenceOrderByCreatedAtAsc(clinicOneId)).hasSize(1);
        assertThat(service.findActiveAssociationsByPublicDoctorReference(doctorId)).hasSize(2);
        assertThat(service.listPublishedPracticeReferencesByDoctor(doctorId)).containsExactly(clinicOneId, clinicTwoId);
        assertThat(associationRepository.findAll()).hasSize(2);
    }

    private DiscoverPublicProviderProfileEntity clinicProfile(UUID providerId, String slug, String displayName) {
        return DiscoverPublicProviderProfileEntity.create(
                providerId,
                ProviderType.CLINIC,
                "HEALTHCARE_CLINIC",
                providerId.toString(),
                NOW.toInstant().toEpochMilli(),
                NOW,
                slug,
                UUID.randomUUID(),
                1,
                displayName,
                displayName,
                displayName + " summary",
                "General Medicine",
                "General Medicine",
                null,
                "Consultation",
                "Outpatient",
                null,
                null,
                null,
                null,
                null,
                null,
                "9876543210",
                "clinic@example.test",
                null,
                "Pune",
                "Wakad",
                "Maharashtra",
                "India",
                null,
                "Private",
                null,
                null,
                null,
                true,
                0,
                0,
                0,
                0,
                "CALL_TO_BOOK",
                NOW
        );
    }

    private DiscoverPublicProviderProfileEntity doctorProfile(UUID providerId, String slug, String displayName) {
        return DiscoverPublicProviderProfileEntity.create(
                providerId,
                ProviderType.INDIVIDUAL_DOCTOR,
                "HEALTHCARE_DOCTOR",
                providerId.toString(),
                NOW.toInstant().toEpochMilli(),
                NOW,
                slug,
                UUID.randomUUID(),
                1,
                displayName,
                displayName,
                displayName + " summary",
                "General Medicine",
                "General Medicine",
                null,
                "Consultation",
                null,
                null,
                "English",
                null,
                null,
                null,
                null,
                "9999999999",
                "doctor@example.test",
                null,
                "Pune",
                "Wakad",
                "Maharashtra",
                "India",
                null,
                null,
                null,
                null,
                null,
                false,
                0,
                0,
                0,
                0,
                "CALL_TO_BOOK",
                NOW
        );
    }
}
