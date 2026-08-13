package com.deepthoughtnet.clinic.discover.publichospitaldoctorassociation.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscoverPublicHospitalDoctorAssociationRepository extends JpaRepository<DiscoverPublicHospitalDoctorAssociationEntity, UUID> {
    Optional<DiscoverPublicHospitalDoctorAssociationEntity> findBySourceSystemIgnoreCaseAndSourceHospitalReferenceAndSourceDoctorReference(
            String sourceSystem,
            UUID sourceHospitalReference,
            UUID sourceDoctorReference
    );

    List<DiscoverPublicHospitalDoctorAssociationEntity> findByPublicHospitalReferenceOrderByCreatedAtAsc(UUID publicHospitalReference);

    List<DiscoverPublicHospitalDoctorAssociationEntity> findByPublicHospitalReferenceAndActiveTrueOrderByCreatedAtAsc(UUID publicHospitalReference);

    List<DiscoverPublicHospitalDoctorAssociationEntity> findByPublicDoctorReferenceOrderByCreatedAtAsc(UUID publicDoctorReference);

    List<DiscoverPublicHospitalDoctorAssociationEntity> findByPublicDoctorReferenceAndActiveTrueOrderByCreatedAtAsc(UUID publicDoctorReference);

    List<DiscoverPublicHospitalDoctorAssociationEntity> findAllByOrderByCreatedAtAsc();
}
