package com.deepthoughtnet.clinic.ai.orchestration.platform.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** AI invocation log writer/query service. */
public interface AiInvocationLogService {
    void record(InvocationLogCommand command);

    List<InvocationLogRecord> recent(UUID tenantId);

    record InvocationLogCommand(UUID tenantId,
                                UUID requestId,
                                String correlationId,
                                String domain,
                                String useCase,
                                String promptKey,
                                Integer promptVersion,
                                String providerName,
                                String modelName,
                                String status,
                                Long inputTokenCount,
                                Long outputTokenCount,
                                BigDecimal estimatedCost,
                                Long latencyMs,
                                String requestPayloadRedacted,
                                String responsePayloadRedacted,
                                String errorCode,
                                String errorMessage,
                                UUID createdBy) {}

    record InvocationLogRecord(UUID id,
                               UUID requestId,
                               String domain,
                               String useCase,
                               String promptKey,
                               Integer promptVersion,
                               String providerName,
                               String modelName,
                               String status,
                               Long inputTokenCount,
                               Long outputTokenCount,
                               BigDecimal estimatedCost,
                               Long latencyMs,
                               String errorCode,
                               String errorMessage,
                               OffsetDateTime createdAt) {}
}
