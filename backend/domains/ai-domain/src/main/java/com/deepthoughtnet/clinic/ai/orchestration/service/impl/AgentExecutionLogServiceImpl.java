package com.deepthoughtnet.clinic.ai.orchestration.service.impl;

import com.deepthoughtnet.clinic.ai.orchestration.db.AgentExecutionLogEntity;
import com.deepthoughtnet.clinic.ai.orchestration.db.AgentExecutionLogRepository;
import com.deepthoughtnet.clinic.ai.orchestration.service.AgentExecutionLogService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentExecutionLogServiceImpl implements AgentExecutionLogService {
    private final AgentExecutionLogRepository repository;

    public AgentExecutionLogServiceImpl(AgentExecutionLogRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void record(UUID tenantId, String agentType, UUID entityId, String suggestionJson, String status, UUID executedBy) {
        if (tenantId == null || agentType == null || status == null) {
            return;
        }
        repository.save(AgentExecutionLogEntity.create(tenantId, agentType, entityId, suggestionJson, status, executedBy));
    }
}
