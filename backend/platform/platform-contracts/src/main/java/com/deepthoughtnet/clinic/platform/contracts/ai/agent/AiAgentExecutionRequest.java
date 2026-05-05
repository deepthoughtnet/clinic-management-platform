package com.deepthoughtnet.clinic.platform.contracts.ai.agent;

import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import java.util.Map;
import java.util.UUID;

public record AiAgentExecutionRequest(
        AiProductCode productCode,
        UUID tenantId,
        UUID actorUserId,
        String workflowCode,
        Map<String, Object> inputVariables,
        String correlationId
) {
}
