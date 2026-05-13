package com.deepthoughtnet.clinic.carepilot.lead.activity.db;

import com.deepthoughtnet.clinic.carepilot.lead.activity.model.LeadActivityType;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadActivityRepository extends JpaRepository<LeadActivityEntity, UUID> {
    Page<LeadActivityEntity> findByTenantIdAndLeadIdOrderByCreatedAtDesc(UUID tenantId, UUID leadId, Pageable pageable);

    List<LeadActivityEntity> findByTenantIdAndLeadIdIn(UUID tenantId, Collection<UUID> leadIds);

    Optional<LeadActivityEntity> findFirstByTenantIdAndLeadIdAndActivityTypeAndRelatedEntityIdOrderByCreatedAtDesc(
            UUID tenantId, UUID leadId, LeadActivityType activityType, UUID relatedEntityId);

    long countByTenantIdAndActivityTypeAndCreatedAtBetween(UUID tenantId, LeadActivityType activityType, OffsetDateTime from, OffsetDateTime to);
}
