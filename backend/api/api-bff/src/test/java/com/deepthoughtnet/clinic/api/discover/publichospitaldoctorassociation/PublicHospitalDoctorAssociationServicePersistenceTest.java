package com.deepthoughtnet.clinic.api.discover.publichospitaldoctorassociation;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.config.PersistenceScanConfig;
import com.deepthoughtnet.clinic.api.support.AbstractPostgresDataJpaTest;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.publichospitaldoctorassociation.PublicHospitalDoctorAssociationService;
import com.deepthoughtnet.clinic.discover.publichospitaldoctorassociation.db.DiscoverPublicHospitalDoctorAssociationRepository;
import com.deepthoughtnet.clinic.discover.publicprofile.db.DiscoverPublicProviderProfileEntity;
import com.deepthoughtnet.clinic.discover.publicprofile.db.DiscoverPublicProviderProfileRepository;
import java.math.BigDecimal;
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
        PublicHospitalDoctorAssociationService.class
})
class PublicHospitalDoctorAssociationServicePersistenceTest extends AbstractPostgresDataJpaTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-08T11:11:36.409Z");

    @Autowired
    private PublicHospitalDoctorAssociationService service;

    @Autowired
    private DiscoverPublicHospitalDoctorAssociationRepository associationRepository;

    @Autowired
    private DiscoverPublicProviderProfileRepository profileRepository;

    @Test
    void addAndRemoveHospitalDoctorsAreIdempotentAndUpdateCounts() {
        UUID hospitalId = UUID.fromString("c0d6f1a5-0c2c-4d94-8cb9-85bb5d0d4001");
        UUID doctorOneId = UUID.fromString("a57d88d7-afac-443d-8a03-9f88e2155df6");
        UUID doctorTwoId = UUID.randomUUID();
        profileRepository.save(hospitalProfile(hospitalId, "jeevanam-multispeciality-hospital", "Jeevanam Multispeciality Hospital"));
        profileRepository.save(doctorProfile(doctorOneId, "neeraj-kulkarni", "Neeraj Kulkarni"));
        profileRepository.save(doctorProfile(doctorTwoId, "amit-verma-2", "Amit Verma"));

        service.upsertActiveAssociation(hospitalId, doctorOneId, PublicHospitalDoctorAssociationService.SOURCE_SYSTEM_DISCOVER_PROVIDER, hospitalId, doctorOneId, NOW);
        service.upsertActiveAssociation(hospitalId, doctorTwoId, PublicHospitalDoctorAssociationService.SOURCE_SYSTEM_DISCOVER_PROVIDER, hospitalId, doctorTwoId, NOW);

        assertThat(associationRepository.findByPublicHospitalReferenceOrderByCreatedAtAsc(hospitalId)).hasSize(2);
        assertThat(service.listPublishedDoctorReferencesByHospital(hospitalId)).containsExactly(doctorOneId, doctorTwoId);
        assertThat(profileRepository.findByProviderId(hospitalId)).isPresent().get()
                .extracting(DiscoverPublicProviderProfileEntity::getDoctorCount)
                .isEqualTo(2);

        service.upsertActiveAssociation(hospitalId, doctorOneId, PublicHospitalDoctorAssociationService.SOURCE_SYSTEM_DISCOVER_PROVIDER, hospitalId, doctorOneId, NOW.plusMinutes(5));
        assertThat(associationRepository.findByPublicHospitalReferenceOrderByCreatedAtAsc(hospitalId)).hasSize(2);

        service.deactivateAssociation(hospitalId, doctorOneId, PublicHospitalDoctorAssociationService.SOURCE_SYSTEM_DISCOVER_PROVIDER, hospitalId, doctorOneId, NOW.plusMinutes(10));
        assertThat(service.listPublishedDoctorReferencesByHospital(hospitalId)).containsExactly(doctorTwoId);
        assertThat(profileRepository.findByProviderId(hospitalId)).isPresent().get()
                .extracting(DiscoverPublicProviderProfileEntity::getDoctorCount)
                .isEqualTo(1);

        service.upsertActiveAssociation(hospitalId, doctorOneId, PublicHospitalDoctorAssociationService.SOURCE_SYSTEM_DISCOVER_PROVIDER, hospitalId, doctorOneId, NOW.plusMinutes(15));
        assertThat(service.listPublishedDoctorReferencesByHospital(hospitalId)).containsExactly(doctorOneId, doctorTwoId);
        assertThat(associationRepository.findAll()).hasSize(2);
    }

    @Test
    void unpublishedDoctorsAreExcludedFromPublishedHospitalLists() {
        UUID hospitalId = UUID.fromString("c0d6f1a5-0c2c-4d94-8cb9-85bb5d0d4001");
        UUID doctorId = UUID.fromString("a57d88d7-afac-443d-8a03-9f88e2155df6");
        profileRepository.save(hospitalProfile(hospitalId, "jeevanam-multispeciality-hospital", "Jeevanam Multispeciality Hospital"));
        DiscoverPublicProviderProfileEntity doctor = profileRepository.save(doctorProfile(doctorId, "neeraj-kulkarni", "Neeraj Kulkarni"));

        service.upsertActiveAssociation(hospitalId, doctorId, PublicHospitalDoctorAssociationService.SOURCE_SYSTEM_DISCOVER_PROVIDER, hospitalId, doctorId, NOW);
        assertThat(service.listPublishedDoctorReferencesByHospital(hospitalId)).containsExactly(doctorId);

        doctor.markUnpublished(NOW.plusMinutes(2));
        profileRepository.save(doctor);
        assertThat(service.listPublishedDoctorReferencesByHospital(hospitalId)).isEmpty();
    }

    private DiscoverPublicProviderProfileEntity hospitalProfile(UUID providerId, String slug, String displayName) {
        return DiscoverPublicProviderProfileEntity.create(
                providerId,
                ProviderType.HOSPITAL,
                "DISCOVER_PROVIDER_PROFILE",
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
                "Inpatient",
                null,
                null,
                null,
                null,
                null,
                null,
                "9876543210",
                "hospital@example.test",
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
                "DISCOVER_PROVIDER_PROFILE",
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
                "General Medicine",
                null,
                null,
                null,
                null,
                true,
                1,
                1,
                0,
                0,
                "ONLINE_BOOKING",
                NOW
        );
    }
}
