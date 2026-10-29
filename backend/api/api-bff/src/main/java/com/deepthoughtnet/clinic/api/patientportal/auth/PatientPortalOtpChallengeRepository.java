package com.deepthoughtnet.clinic.api.patientportal.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientPortalOtpChallengeRepository extends JpaRepository<PatientPortalOtpChallengeEntity, UUID> {
    boolean existsByTenantIdAndIdentifiedPatientIdAndPhoneNormalizedAndChannelAndVerifiedAtIsNotNull(
            UUID tenantId, UUID identifiedPatientId, String phoneNormalized, String channel);
    Optional<PatientPortalOtpChallengeEntity> findTopByPhoneNormalizedOrderByCreatedAtDesc(String phoneNormalized);

    Optional<PatientPortalOtpChallengeEntity> findTopByTenantIdAndPhoneNormalizedOrderByCreatedAtDesc(
            UUID tenantId,
            String phoneNormalized
    );

    Optional<PatientPortalOtpChallengeEntity> findTopByTenantIdAndPhoneNormalizedAndChannelIsNullOrderByCreatedAtDesc(
            UUID tenantId, String phoneNormalized);

    Optional<PatientPortalOtpChallengeEntity> findTopByPhoneNormalizedAndChannelIsNullOrderByCreatedAtDesc(
            String phoneNormalized);
}
