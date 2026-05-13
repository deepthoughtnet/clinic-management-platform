package com.deepthoughtnet.clinic.carepilot.webinar.db;

import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarRegistrationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebinarRegistrationRepository extends JpaRepository<WebinarRegistrationEntity, UUID> {
    Page<WebinarRegistrationEntity> findByTenantIdAndWebinarIdOrderByCreatedAtDesc(UUID tenantId, UUID webinarId, Pageable pageable);

    Optional<WebinarRegistrationEntity> findByTenantIdAndWebinarIdAndAttendeeEmail(UUID tenantId, UUID webinarId, String attendeeEmail);

    long countByTenantIdAndWebinarId(UUID tenantId, UUID webinarId);

    long countByTenantIdAndWebinarIdAndAttendedTrue(UUID tenantId, UUID webinarId);

    long countByTenantIdAndWebinarIdAndRegistrationStatus(UUID tenantId, UUID webinarId, WebinarRegistrationStatus status);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndAttendedTrue(UUID tenantId);

    long countByTenantIdAndRegistrationStatus(UUID tenantId, WebinarRegistrationStatus status);

    List<WebinarRegistrationEntity> findByTenantId(UUID tenantId);

    List<WebinarRegistrationEntity> findByTenantIdAndWebinarIdAndRegistrationStatusNot(UUID tenantId, UUID webinarId, WebinarRegistrationStatus status);

    List<WebinarRegistrationEntity> findByTenantIdAndPatientId(UUID tenantId, UUID patientId);
}
