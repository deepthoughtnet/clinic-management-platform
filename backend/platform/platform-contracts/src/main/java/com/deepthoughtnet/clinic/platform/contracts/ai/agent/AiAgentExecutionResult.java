package com.deepthoughtnet.clinic.platform.contracts.ai.agent;

public record AiAgentExecutionResult(
        String workflowCode,
        String outputJson,
        String status,
        boolean fallbackUsed,
        String message
) {
}
