package com.deepthoughtnet.clinic.discover.publicdoctorpracticeassociation.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscoverPublicDoctorPracticeAssociationRepository extends JpaRepository<DiscoverPublicDoctorPracticeAssociationEntity, UUID> {
    Optional<DiscoverPublicDoctorPracticeAssociationEntity> findBySourceSystemIgnoreCaseAndSourceDoctorReferenceAndSourcePracticeReference(
            String sourceSystem,
            UUID sourceDoctorReference,
            UUID sourcePracticeReference
    );

    List<DiscoverPublicDoctorPracticeAssociationEntity> findByPublicDoctorReferenceOrderByCreatedAtAsc(UUID publicDoctorReference);

    List<DiscoverPublicDoctorPracticeAssociationEntity> findByPublicDoctorReferenceAndActiveTrueOrderByCreatedAtAsc(UUID publicDoctorReference);

    List<DiscoverPublicDoctorPracticeAssociationEntity> findByPublicPracticeReferenceOrderByCreatedAtAsc(UUID publicPracticeReference);

    List<DiscoverPublicDoctorPracticeAssociationEntity> findByPublicPracticeReferenceAndActiveTrueOrderByCreatedAtAsc(UUID publicPracticeReference);

    List<DiscoverPublicDoctorPracticeAssociationEntity> findBySourceSystemIgnoreCaseAndSourcePracticeReferenceOrderByCreatedAtAsc(
            String sourceSystem,
            UUID sourcePracticeReference
    );

    List<DiscoverPublicDoctorPracticeAssociationEntity> findAllByOrderByCreatedAtAsc();
}
