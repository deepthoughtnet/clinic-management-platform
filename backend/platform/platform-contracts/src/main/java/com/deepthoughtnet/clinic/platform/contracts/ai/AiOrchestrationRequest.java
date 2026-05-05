package com.deepthoughtnet.clinic.platform.contracts.ai;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AiOrchestrationRequest(
        AiProductCode productCode,
        UUID tenantId,
        UUID actorUserId,
        AiTaskType taskType,
        String promptTemplateCode,
        Map<String, Object> inputVariables,
        List<AiEvidenceReference> evidence,
        Integer maxTokens,
        Double temperature,
        String correlationId,
        String useCaseCode
) {
}
