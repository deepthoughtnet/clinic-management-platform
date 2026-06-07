package com.deepthoughtnet.clinic.ai.careai.persistence.db;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareAiWorkflowEventRepository extends JpaRepository<CareAiWorkflowEventEntity, UUID> {
}
