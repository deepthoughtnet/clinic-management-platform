package com.deepthoughtnet.clinic.ai.orchestration.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiPromptTemplateRepository extends JpaRepository<AiPromptTemplateEntity, UUID> {
    List<AiPromptTemplateEntity> findByTemplateCodeAndStatusOrderByUpdatedAtDesc(String templateCode, String status);
}
