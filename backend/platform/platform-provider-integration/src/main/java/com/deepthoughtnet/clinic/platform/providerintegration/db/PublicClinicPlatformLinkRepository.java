package com.deepthoughtnet.clinic.platform.providerintegration.db;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicClinicPlatformLinkRepository extends JpaRepository<PublicClinicPlatformLinkEntity, UUID> {
    Optional<PublicClinicPlatformLinkEntity> findByPublicClinicReferenceAndTenantReferenceAndPlatformClinicReference(
            String publicClinicReference,
            String tenantReference,
            String platformClinicReference
    );

    Optional<PublicClinicPlatformLinkEntity> findByBookingReferenceAndActiveTrue(String bookingReference);
}
