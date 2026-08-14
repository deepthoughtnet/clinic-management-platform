package com.deepthoughtnet.clinic.patient.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.deepthoughtnet.clinic.patient.service.model.PatientPortalAccessRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientPortalAccessRequestRepository extends JpaRepository<PatientPortalAccessRequestEntity, UUID> {
    List<PatientPortalAccessRequestEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<PatientPortalAccessRequestEntity> findTopByTenantIdAndMobileNormalizedOrderByCreatedAtDesc(UUID tenantId, String mobileNormalized);

    List<PatientPortalAccessRequestEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, PatientPortalAccessRequestStatus status);

    Optional<PatientPortalAccessRequestEntity> findByTenantIdAndId(UUID tenantId, UUID id);
}
