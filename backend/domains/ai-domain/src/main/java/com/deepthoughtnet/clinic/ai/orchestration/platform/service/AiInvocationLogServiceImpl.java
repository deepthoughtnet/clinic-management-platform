package com.deepthoughtnet.clinic.ai.orchestration.platform.service;

import com.deepthoughtnet.clinic.ai.orchestration.platform.db.AiInvocationLogEntity;
import com.deepthoughtnet.clinic.ai.orchestration.platform.db.AiInvocationLogRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Writes and queries invocation logs with optional redacted payload storage. */
@Service
public class AiInvocationLogServiceImpl implements AiInvocationLogService {
    private final AiInvocationLogRepository repository;
    private final boolean storePayloads;

    public AiInvocationLogServiceImpl(AiInvocationLogRepository repository,
                                      @Value("${ai.audit.store-payloads:false}") boolean storePayloads) {
        this.repository = repository;
        this.storePayloads = storePayloads;
    }

    @Override
    public void record(InvocationLogCommand command) {
        repository.save(AiInvocationLogEntity.create(
                command.tenantId(),
                command.requestId(),
                command.correlationId(),
                command.domain(),
                command.useCase(),
                command.promptKey(),
                command.promptVersion(),
                command.providerName(),
                command.modelName(),
                command.status(),
                command.inputTokenCount(),
                command.outputTokenCount(),
                command.estimatedCost(),
                command.latencyMs(),
                storePayloads ? command.requestPayloadRedacted() : null,
                storePayloads ? command.responsePayloadRedacted() : null,
                command.errorCode(),
                command.errorMessage(),
                command.createdBy()
        ));
    }

    @Override
    public List<InvocationLogRecord> recent(UUID tenantId) {
        return repository.findTop200ByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(row -> new InvocationLogRecord(
                        row.getId(),
                        row.getRequestId(),
                        row.getDomain(),
                        row.getUseCase(),
                        row.getPromptKey(),
                        row.getPromptVersion(),
                        row.getProviderName(),
                        row.getModelName(),
                        row.getStatus(),
                        row.getInputTokenCount(),
                        row.getOutputTokenCount(),
                        row.getEstimatedCost(),
                        row.getLatencyMs(),
                        row.getErrorCode(),
                        row.getErrorMessage(),
                        row.getCreatedAt()
                ))
                .toList();
    }
}
