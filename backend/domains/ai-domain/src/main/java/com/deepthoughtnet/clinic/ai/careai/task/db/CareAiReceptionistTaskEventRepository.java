package com.deepthoughtnet.clinic.ai.careai.task.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareAiReceptionistTaskEventRepository extends JpaRepository<CareAiReceptionistTaskEventEntity, UUID> {
    List<CareAiReceptionistTaskEventEntity> findByTenantIdAndTaskIdOrderByCreatedAtAsc(UUID tenantId, UUID taskId);
}
