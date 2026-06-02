package com.deepthoughtnet.clinic.api.patientportal.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientPortalOtpChallengeRepository extends JpaRepository<PatientPortalOtpChallengeEntity, UUID> {
    Optional<PatientPortalOtpChallengeEntity> findTopByTenantIdAndPhoneNormalizedOrderByCreatedAtDesc(
            UUID tenantId,
            String phoneNormalized
    );
}
