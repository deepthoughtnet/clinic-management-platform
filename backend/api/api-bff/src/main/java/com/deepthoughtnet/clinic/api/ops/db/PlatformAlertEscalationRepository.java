package com.deepthoughtnet.clinic.api.ops.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Escalation persistence repository. */
public interface PlatformAlertEscalationRepository extends JpaRepository<PlatformAlertEscalationEntity, UUID> {
    List<PlatformAlertEscalationEntity> findByAlertIdOrderByEscalationLevelAsc(UUID alertId);
}
