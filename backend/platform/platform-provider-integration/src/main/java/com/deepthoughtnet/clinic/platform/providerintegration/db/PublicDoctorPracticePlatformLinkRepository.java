package com.deepthoughtnet.clinic.platform.providerintegration.db;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicDoctorPracticePlatformLinkRepository extends JpaRepository<PublicDoctorPracticePlatformLinkEntity, UUID> {
    Optional<PublicDoctorPracticePlatformLinkEntity> findByPublicDoctorReferenceAndPublicPracticeReferenceAndTenantReferenceAndPlatformClinicReferenceAndTenantDoctorUserReference(
            String publicDoctorReference,
            String publicPracticeReference,
            String tenantReference,
            String platformClinicReference,
            String tenantDoctorUserReference
    );

    Optional<PublicDoctorPracticePlatformLinkEntity> findByBookingReferenceAndActiveTrue(String bookingReference);
}
