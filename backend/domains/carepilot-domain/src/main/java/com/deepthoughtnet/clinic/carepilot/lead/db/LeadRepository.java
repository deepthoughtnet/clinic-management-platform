package com.deepthoughtnet.clinic.carepilot.lead.db;

import com.deepthoughtnet.clinic.carepilot.lead.model.LeadStatus;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LeadRepository extends JpaRepository<LeadEntity, UUID>, JpaSpecificationExecutor<LeadEntity> {
    Optional<LeadEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, LeadStatus status);

    long countByTenantIdAndStatusIn(UUID tenantId, Collection<LeadStatus> statuses);

    long countByTenantIdAndStatusNotIn(UUID tenantId, Collection<LeadStatus> statuses);

    long countByTenantIdAndNextFollowUpAtLessThanEqualAndStatusNotIn(UUID tenantId, OffsetDateTime cutoff, Collection<LeadStatus> statuses);

    List<LeadEntity> findByTenantIdAndCreatedAtBetween(UUID tenantId, OffsetDateTime from, OffsetDateTime to);

    List<LeadEntity> findByTenantIdAndNextFollowUpAtLessThanEqualAndStatusNotIn(UUID tenantId, OffsetDateTime cutoff, Collection<LeadStatus> statuses);

    long countByTenantIdAndConvertedPatientIdIsNotNullAndBookedAppointmentIdIsNotNull(UUID tenantId);
}
