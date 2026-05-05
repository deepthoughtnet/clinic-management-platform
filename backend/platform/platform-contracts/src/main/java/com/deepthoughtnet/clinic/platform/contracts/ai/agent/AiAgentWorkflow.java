package com.deepthoughtnet.clinic.platform.contracts.ai.agent;

public interface AiAgentWorkflow {
    String workflowCode();

    AiAgentExecutionResult execute(AiAgentExecutionRequest request);
}
