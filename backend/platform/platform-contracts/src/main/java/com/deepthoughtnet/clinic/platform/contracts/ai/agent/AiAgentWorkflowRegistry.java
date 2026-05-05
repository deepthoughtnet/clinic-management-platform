package com.deepthoughtnet.clinic.platform.contracts.ai.agent;

import java.util.List;
import java.util.Optional;

public interface AiAgentWorkflowRegistry {
    Optional<AiAgentWorkflow> find(String workflowCode);

    List<AiAgentWorkflow> list();
}
