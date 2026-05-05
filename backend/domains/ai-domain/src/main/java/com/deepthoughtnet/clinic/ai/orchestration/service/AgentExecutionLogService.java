package com.deepthoughtnet.clinic.ai.orchestration.service;

import java.util.UUID;

public interface AgentExecutionLogService {
    void record(UUID tenantId, String agentType, UUID entityId, String suggestionJson, String status, UUID executedBy);
}
