package com.deepthoughtnet.clinic.carepilot.webinar.db;

import com.deepthoughtnet.clinic.carepilot.webinar.model.WebinarStatus;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WebinarRepository extends JpaRepository<WebinarEntity, UUID>, JpaSpecificationExecutor<WebinarEntity> {
    Optional<WebinarEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<WebinarEntity> findByTenantIdAndStatusInAndScheduledStartAtBetween(
            UUID tenantId,
            Collection<WebinarStatus> statuses,
            OffsetDateTime from,
            OffsetDateTime to
    );

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, WebinarStatus status);
}
