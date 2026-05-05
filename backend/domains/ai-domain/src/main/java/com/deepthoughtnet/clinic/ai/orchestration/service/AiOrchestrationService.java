package com.deepthoughtnet.clinic.ai.orchestration.service;

import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiOrchestrationResponse;

public interface AiOrchestrationService {
    AiOrchestrationResponse complete(AiOrchestrationRequest request);
}
