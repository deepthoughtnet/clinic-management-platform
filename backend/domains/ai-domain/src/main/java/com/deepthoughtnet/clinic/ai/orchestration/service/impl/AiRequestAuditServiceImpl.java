package com.deepthoughtnet.clinic.ai.orchestration.service.impl;

import com.deepthoughtnet.clinic.ai.orchestration.db.AiRequestAuditEntity;
import com.deepthoughtnet.clinic.ai.orchestration.db.AiRequestAuditRepository;
import com.deepthoughtnet.clinic.ai.orchestration.service.AiRequestAuditService;
import com.deepthoughtnet.clinic.ai.orchestration.service.model.AiRequestAuditCommand;
import org.springframework.stereotype.Service;

@Service
public class AiRequestAuditServiceImpl implements AiRequestAuditService {
    private final AiRequestAuditRepository repository;

    public AiRequestAuditServiceImpl(AiRequestAuditRepository repository) {
        this.repository = repository;
    }

    @Override
    public AiRequestAuditEntity record(AiRequestAuditCommand command) {
        return repository.save(AiRequestAuditEntity.create(
                command.id(),
                command.productCode(),
                command.tenantId(),
                command.actorAppUserId(),
                command.useCaseCode(),
                command.taskType(),
                command.promptTemplateCode(),
                command.promptTemplateVersion(),
                command.provider(),
                command.model(),
                command.requestHash(),
                command.inputSummary(),
                command.outputSummary(),
                command.status(),
                command.confidence(),
                command.latencyMs(),
                command.inputTokens(),
                command.outputTokens(),
                command.totalTokens(),
                command.estimatedCost(),
                command.fallbackUsed(),
                command.errorMessage(),
                command.correlationId()
        ));
    }
}
