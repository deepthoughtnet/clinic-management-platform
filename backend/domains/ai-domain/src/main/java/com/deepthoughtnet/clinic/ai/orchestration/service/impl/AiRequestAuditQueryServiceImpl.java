package com.deepthoughtnet.clinic.ai.orchestration.service.impl;

import com.deepthoughtnet.clinic.ai.orchestration.db.AiRequestAuditEntity;
import com.deepthoughtnet.clinic.ai.orchestration.db.AiRequestAuditRepository;
import com.deepthoughtnet.clinic.ai.orchestration.service.AiRequestAuditQueryService;
import com.deepthoughtnet.clinic.ai.orchestration.service.model.AiRecentRequestRecord;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AiRequestAuditQueryServiceImpl implements AiRequestAuditQueryService {
    private final AiRequestAuditRepository repository;

    public AiRequestAuditQueryServiceImpl(AiRequestAuditRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<AiRecentRequestRecord> recent(UUID tenantId, AiProductCode productCode) {
        if (tenantId == null || productCode == null) {
            return List.of();
        }
        return repository.findTop20ByTenantIdAndProductCodeOrderByCreatedAtDesc(tenantId, productCode.name())
                .stream()
                .map(this::toRecord)
                .toList();
    }

    private AiRecentRequestRecord toRecord(AiRequestAuditEntity entity) {
        return new AiRecentRequestRecord(
                entity.getId(),
                entity.getProductCode(),
                entity.getTenantId(),
                entity.getActorAppUserId(),
                entity.getUseCaseCode(),
                entity.getTaskType(),
                entity.getPromptTemplateCode(),
                entity.getPromptTemplateVersion(),
                entity.getProvider(),
                entity.getModel(),
                entity.getStatus(),
                entity.getConfidence(),
                entity.getLatencyMs(),
                entity.isFallbackUsed(),
                entity.getInputSummary(),
                entity.getOutputSummary(),
                entity.getCorrelationId(),
                entity.getCreatedAt()
        );
    }
}
