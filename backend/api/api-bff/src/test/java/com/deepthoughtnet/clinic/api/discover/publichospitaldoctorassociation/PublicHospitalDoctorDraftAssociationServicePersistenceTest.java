package com.deepthoughtnet.clinic.api.discover.publichospitaldoctorassociation;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.config.PersistenceScanConfig;
import com.deepthoughtnet.clinic.api.support.AbstractPostgresDataJpaTest;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.publichospitaldoctorassociation.PublicHospitalDoctorAssociationService;
import com.deepthoughtnet.clinic.discover.publichospitaldoctorassociation.PublicHospitalDoctorDraftAssociationService;
import com.deepthoughtnet.clinic.discover.publichospitaldoctorassociation.db.DiscoverPublicHospitalDoctorDraftAssociationRepository;
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
        PublicHospitalDoctorAssociationService.class,
        PublicHospitalDoctorDraftAssociationService.class
})
class PublicHospitalDoctorDraftAssociationServicePersistenceTest extends AbstractPostgresDataJpaTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-11T10:30:00Z");

    @Autowired
    private PublicHospitalDoctorAssociationService publishedService;

    @Autowired
    private PublicHospitalDoctorDraftAssociationService draftService;

    @Autowired
    private DiscoverPublicHospitalDoctorDraftAssociationRepository draftRepository;

    @Autowired
    private DiscoverPublicProviderProfileRepository profileRepository;

    @Test
    void draftAssociationEditsDoNotMutatePublishedAssociationsUntilPublish() {
        UUID hospitalId = UUID.fromString("2206731d-3f34-426f-b069-2abca255f988");
        UUID doctorOneId = UUID.fromString("23cf0f04-3152-46ef-a0f6-3b243f90bbc5");
        UUID doctorTwoId = UUID.fromString("a57d88d7-afac-443d-8a03-9f88e2155df6");
        profileRepository.save(hospitalProfile(hospitalId, "jeevanam-multispeciality-hospital", "Jeevanam Multispeciality Hospital"));
        profileRepository.save(doctorProfile(doctorOneId, "amit-verma-2", "Amit Verma"));
        profileRepository.save(doctorProfile(doctorTwoId, "neeraj-kulkarni", "Neeraj Kulkarni"));

        publishedService.upsertActiveAssociation(hospitalId, doctorOneId, PublicHospitalDoctorAssociationService.SOURCE_SYSTEM_DISCOVER_PROVIDER, hospitalId, doctorOneId, NOW);

        assertThat(publishedService.listPublishedDoctorReferencesByHospital(hospitalId)).containsExactly(doctorOneId);
        assertThat(draftService.listDraftDoctorReferencesByHospital(hospitalId)).containsExactly(doctorOneId);
        assertThat(draftRepository.findByPublicHospitalReferenceOrderByCreatedAtAsc(hospitalId)).hasSize(1);

        draftService.upsertActiveAssociation(hospitalId, doctorTwoId, PublicHospitalDoctorDraftAssociationService.SOURCE_SYSTEM_DISCOVER_PROVIDER, hospitalId, doctorTwoId, NOW.plusMinutes(1));

        assertThat(draftService.listDraftDoctorReferencesByHospital(hospitalId)).containsExactlyInAnyOrder(doctorOneId, doctorTwoId);
        assertThat(publishedService.listPublishedDoctorReferencesByHospital(hospitalId)).containsExactly(doctorOneId);

        draftService.deactivateAssociation(hospitalId, doctorOneId, PublicHospitalDoctorDraftAssociationService.SOURCE_SYSTEM_DISCOVER_PROVIDER, hospitalId, doctorOneId, NOW.plusMinutes(2));

        assertThat(draftService.listDraftDoctorReferencesByHospital(hospitalId)).containsExactly(doctorTwoId);
        assertThat(publishedService.listPublishedDoctorReferencesByHospital(hospitalId)).containsExactly(doctorOneId);
    }

    private DiscoverPublicProviderProfileEntity hospitalProfile(UUID providerId, String slug, String displayName) {
        return DiscoverPublicProviderProfileEntity.create(
                providerId,
                ProviderType.HOSPITAL,
                "DISCOVER_ONBOARDING_APPLICATION",
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
                "DISCOVER_ONBOARDING_APPLICATION",
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
