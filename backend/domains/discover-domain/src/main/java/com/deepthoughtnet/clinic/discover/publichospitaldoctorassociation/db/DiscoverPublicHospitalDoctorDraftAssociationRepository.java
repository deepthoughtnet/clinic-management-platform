package com.deepthoughtnet.clinic.discover.publichospitaldoctorassociation.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscoverPublicHospitalDoctorDraftAssociationRepository extends JpaRepository<DiscoverPublicHospitalDoctorDraftAssociationEntity, UUID> {
    Optional<DiscoverPublicHospitalDoctorDraftAssociationEntity> findBySourceSystemIgnoreCaseAndSourceHospitalReferenceAndSourceDoctorReference(
            String sourceSystem,
            UUID sourceHospitalReference,
            UUID sourceDoctorReference
    );

    List<DiscoverPublicHospitalDoctorDraftAssociationEntity> findByPublicHospitalReferenceOrderByCreatedAtAsc(UUID publicHospitalReference);

    List<DiscoverPublicHospitalDoctorDraftAssociationEntity> findByPublicHospitalReferenceAndActiveTrueOrderByCreatedAtAsc(UUID publicHospitalReference);

    List<DiscoverPublicHospitalDoctorDraftAssociationEntity> findByPublicDoctorReferenceOrderByCreatedAtAsc(UUID publicDoctorReference);

    List<DiscoverPublicHospitalDoctorDraftAssociationEntity> findByPublicDoctorReferenceAndActiveTrueOrderByCreatedAtAsc(UUID publicDoctorReference);

    List<DiscoverPublicHospitalDoctorDraftAssociationEntity> findAllByOrderByCreatedAtAsc();

    boolean existsByPublicHospitalReference(UUID publicHospitalReference);
}
